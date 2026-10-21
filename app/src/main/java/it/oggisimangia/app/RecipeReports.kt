package it.oggisimangia.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.util.UUID

object RecipeReports {
    data class Report(val file: File, val pages: Int)

    fun create(context: android.content.Context, title: String, subtitle: String, recipes: List<Recipe>, ranges: Array<PageRange>? = null): Report {
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        var canvas = Canvas()
        var number = 0
        var y = 0f
        val green = Color.rgb(31,78,49)
        fun newPage() {
            page?.let { document.finishPage(it) }
            number++
            val included = ranges == null || ranges.any { number-1 in it.start..it.end }
            page = if (included) document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create()) else null
            canvas = page?.canvas ?: Canvas()
            val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=green; textSize=20f; typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD) }
            canvas.drawText("OggiSiMangia...",40f,45f,heading)
            heading.textSize=13f; canvas.drawText(title,40f,68f,heading)
            heading.textSize=9f; heading.typeface=Typeface.DEFAULT
            canvas.drawText("Pagina $number",495f,810f,heading)
            canvas.drawLine(40f,790f,555f,790f,heading)
            y=90f
        }
        fun paragraph(text: String, size: Float = 11f, bold: Boolean = false) {
            if (text.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color=if(bold) green else Color.rgb(35,35,35); textSize=size
                typeface=Typeface.create(Typeface.DEFAULT,if(bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(text,0,text.length,paint,515)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            for(i in 0 until layout.lineCount) {
                val height = size*1.5f
                if(y+height>770f) newPage()
                val line = text.substring(layout.getLineStart(i),layout.getLineEnd(i)).trimEnd('\n','\r')
                canvas.drawText(line,40f,y+size,paint)
                y+=height
            }
            y+=7f
        }
        val folder = File(context.cacheDir,"reports").apply { mkdirs() }
        val file = File(folder,"OggiSiMangia-${UUID.randomUUID().toString().take(8)}.pdf")
        try {
            newPage()
            paragraph(subtitle,13f,true)
            if(recipes.isEmpty()) paragraph("Nessuna ricetta pianificata per questo giorno.")
            recipes.forEachIndexed { index, recipe ->
                if(y>640f) newPage()
                if(index>0) y+=12f
                paragraph(recipe.meal + " - " + (if(recipe.alternative) "Alternativa" else "Principale"),12f,true)
                paragraph(recipe.title,16f,true)
                paragraph(recipe.category + " | " + recipe.difficulty + " | " + recipe.day.ifBlank { "Ricettario" },10f)
                val metadata = listOf("Dosi" to recipe.portions,"Preparazione" to recipe.prepTime,
                    "Cottura" to recipe.cookTime,"Totale" to recipe.totalTime,"Riposo" to recipe.restTime,
                    "Valori nutrizionali" to recipe.nutrition).filter { it.second.isNotBlank() }
                metadata.forEach { paragraph(it.first + ": " + it.second,10f) }
                if(metadata.none { it.first in listOf("Preparazione","Cottura","Totale") }) paragraph("Tempo: "+recipe.duration,10f)
                if(recipe.imagePath.isNotBlank()) {
                    val bitmap=BitmapFactory.decodeFile(recipe.imagePath)
                    if(bitmap!=null) {
                        val height=minOf(170f,515f*bitmap.height/bitmap.width)
                        if(y+height>770f) newPage()
                        val width=height*bitmap.width/bitmap.height
                        canvas.drawBitmap(bitmap,null,RectF(40f,y,40f+width,y+height),null)
                        bitmap.recycle();y+=height+12f
                    }
                }
                paragraph("Ingredienti",12f,true)
                paragraph(recipe.ingredients.ifBlank { "Non disponibili" })
                if(recipe.instructions.isNotBlank()) { paragraph("Preparazione semplificata / appunti",12f,true); paragraph(recipe.instructions) }
                if(recipe.originalInstructions.isNotBlank()) { paragraph("Procedimento originale",12f,true); paragraph(recipe.originalInstructions) }
                if(recipe.instructions.isBlank() && recipe.originalInstructions.isBlank()) paragraph("Procedimento non disponibile: consulta la fonte originale.")
                if(recipe.url.isNotBlank()) { paragraph("Fonte originale",10f,true); paragraph(recipe.url,9f) }
            }
            page?.let { document.finishPage(it) };page=null
            file.outputStream().use { document.writeTo(it) }
            return Report(file,number)
        } finally { document.close() }
    }

    fun show(activity: Activity, title: String, subtitle: String, recipes: List<Recipe>, save: (File) -> Unit) {
        val progress=AlertDialog.Builder(activity).setMessage("Preparo il report PDF...").setCancelable(false).create()
        progress.show()
        Thread {
            val report=try { create(activity,title,subtitle,recipes) } catch (_: Exception) { null }
            activity.runOnUiThread {
                if(activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                progress.dismiss()
                if(report==null) { AlertDialog.Builder(activity).setMessage("Impossibile creare il PDF").setPositiveButton("Chiudi",null).show();return@runOnUiThread }
                AlertDialog.Builder(activity).setTitle("Report PDF pronto")
                    .setMessage("${report.pages} pagine. Salva il file oppure scegli una stampante nel servizio di stampa Android.")
                    .setPositiveButton("Salva PDF") { _,_ -> save(report.file) }
                    .setNeutralButton("Stampa") { _,_ ->
                        val manager=activity.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                        manager.print("OggiSiMangia - $title",object : PrintDocumentAdapter() {
                            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancellation: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                                if(cancellation?.isCanceled==true) { callback.onLayoutCancelled();return }
                                callback.onLayoutFinished(PrintDocumentInfo.Builder(report.file.name)
                                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(report.pages).build(),true)
                            }
                            override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellation: CancellationSignal, callback: WriteResultCallback) {
                                Thread {
                                    try {
                                        if(cancellation.isCanceled) { callback.onWriteCancelled();return@Thread }
                                        val output = if(pages.any { it==PageRange.ALL_PAGES }) report else create(activity,title,subtitle,recipes,pages)
                                        java.io.FileOutputStream(destination.fileDescriptor).use { target ->
                                            output.file.inputStream().use { source ->
                                                val buffer=ByteArray(8192)
                                                while(true) {
                                                    if(cancellation.isCanceled) { callback.onWriteCancelled();return@Thread }
                                                    val read=source.read(buffer);if(read<0) break;target.write(buffer,0,read)
                                                }
                                            }
                                        }
                                        callback.onWriteFinished(pages)
                                    } catch (_: Exception) { callback.onWriteFailed("Impossibile preparare la stampa") }
                                }.start()
                            }
                        },PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                    }
                    .setNegativeButton("Chiudi",null).show()
            }
        }.start()
    }
}
