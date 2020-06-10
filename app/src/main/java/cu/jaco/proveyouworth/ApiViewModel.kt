package cu.jaco.proveyouworth

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLHandshakeException


class ApiViewModel : ViewModel() {

    private var sessionId: String = ""
    private var token: String = ""
    private lateinit var resume: File
    private lateinit var code: File

    companion object {
        const val TAG = "ProveYouWorth"

        const val NAME = "Osvel Alvarez"
        const val EMAIL = "alvarezosvel@gmail.com"
        const val ABOUT_ME = "Android Developer (Kotlin, Java, and some of Flutter)"
        const val CODE = "ProveYouWorth.zip"
        const val RESUME = "cv.pdf"
    }

    fun main(context: Context) = viewModelScope.launch {

        code = context.getOutputFile(CODE)
        resume = context.getOutputFile(RESUME)

        copyAsset(context, CODE, code.absolutePath)
        copyAsset(context, RESUME, resume.absolutePath)

        start()
        val imgPath = downloadImage(
            context,
            "https://www.proveyourworth.net/level3/img/proof.jpg",
            context.getOutputFile()
        )
        Log.d(TAG, imgPath ?: "null")
        submit(context)

    }

    private suspend fun start() = withContext(Dispatchers.IO) {

        try {

            val connection = Jsoup.connect("https://www.proveyourworth.net/level3/start")

            val request = connection.execute()
            val headers = request.headers()

            for (header in headers) {
                Log.d(TAG, "${header.key} = ${header.value}")

                if (header.key == "Set-Cookie") {
                    sessionId = header.value
                    Log.d(TAG, "Session Id = ${header.value}")
                }
            }

            val body = request.parse()
            val inputs = body.select("input")
            for (input in inputs) {

                val name = input.attr("name")
                if (name == "statefulhash") {
                    token = input.attr("value")
                    Log.d(TAG, "Token: $token")
                }

            }

        } catch (e: SSLHandshakeException) {

        }

    }

    private suspend fun downloadImage(context: Context, fromUrl: String, toFile: File): String? =
        withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var copy: InputStream? = null
            var imagePath: String? = null

            try {
                val url = URL(fromUrl)
                copy = getInputStream(url)
                inputStream = getInputStream(url)

                // Convert the InputStream into a string
                imagePath = writeBitmapToFile(inputStream, copy, toFile, context.resources)

            } catch (ignore: IOException) {

            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                null
            }

            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                copy?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (imagePath != null && imagePath.isNotEmpty()) {
                //TODO: Do something with downloaded image
            }

            if (imagePath != null && imagePath.isNotEmpty()) imagePath else null
        }

    private suspend fun submit(context: Context) = withContext(Dispatchers.IO) {

        val image = context.getOutputFile()

        val document: Document = Jsoup.connect("https://www.proveyourworth.net/level3/reaper")
            .header("Cookie", sessionId)
            .data("name", NAME)
            .data("email", EMAIL)
            .data("aboutme", ABOUT_ME)
            .data("image", image.name, FileInputStream(image))
            .data("code", image.name, FileInputStream(code))
            .data("resume", image.name, FileInputStream(resume))
            .post()

        Log.d(TAG, document.toString())

    }

    @Throws(IOException::class)
    private fun getInputStream(url: URL): InputStream {

        val conn = url.openConnection() as HttpURLConnection
        conn.readTimeout = 10000
        conn.connectTimeout = 15000
        conn.requestMethod = "GET"
        conn.doInput = true
        // Starts the query
        conn.connect()
        return conn.inputStream

    }

    private fun writeBitmapToFile(
        stream: InputStream,
        copy: InputStream,
        image: File,
        res: Resources
    ): String? {

        if (image.exists())
            image.delete()

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(copy, null, options)
        val imageWidth = options.outWidth
        val imageHeight = options.outHeight

        val mInSampleWidth = imageWidth / (res.displayMetrics.widthPixels / 3)
        val mInSampleHeight = imageHeight / (res.displayMetrics.heightPixels / 5)
        val mInSampleSize = mInSampleWidth.coerceAtMost(mInSampleHeight)
        options.inSampleSize = mInSampleSize.coerceAtLeast(1)
        options.inJustDecodeBounds = false

        val bmp =
            BitmapFactory.decodeStream(stream, null, options)?.copy(Bitmap.Config.RGB_565, false)
        if (bmp != null)
            writeBitmapToFile(bmp, image)

        return image.absolutePath

    }

    private fun writeBitmapToFile(bmp: Bitmap, image: File, sign: String? = NAME) {

        val out: FileOutputStream

        try {
            out = FileOutputStream(image.absolutePath)

            val mutableBitmap: Bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)

            if (sign != null) {
                //sign image
                val hash = Paint()
                hash.color = Color.WHITE // Text Color
                hash.textSize = 40F // Text Size
                hash.xfermode =
                    PorterDuffXfermode(PorterDuff.Mode.SRC_OVER) // Text Overlapping Pattern

                // some more settings...
                canvas.drawBitmap(mutableBitmap, 0F, 0F, hash)
                canvas.drawText(token, 10F, 250F, hash)

                val name = Paint()
                name.color = Color.WHITE // Text Color
                name.textSize = 80F // Text Size
                name.xfermode =
                    PorterDuffXfermode(PorterDuff.Mode.SRC_OVER) // Text Overlapping Pattern

                canvas.drawText(sign, 10F, 60F, hash)
            }

            //write the image bitmap at the destination specified by filename.
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        try {
            out.flush()
            out.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }

    }

    private fun Context.getOutputFile(fileName: String = "image.jpg"): File {
        val mediaStorageDir = filesDir
        return File(mediaStorageDir, fileName)
    }

    @Throws(IOException::class)
    private suspend fun copyAsset(context: Context, assetFile: String, toFile: String) =
        withContext(Dispatchers.IO) {

            val outFile = File(toFile)
            val removed = outFile.delete() //try to delete before copy new
            Log.d(TAG, "Removed: $removed")

            //create empty file
            outFile.createNewFile()

            //open input stream
            val myInput = context.assets.open(assetFile)

            //create folder if needed
            val file = File(toFile)
            file.mkdirs()

            //Open the output stream
            val myOutput = FileOutputStream(toFile)

            copyFile(myInput, myOutput)

            //Close the streams
            myOutput.flush()
            myOutput.close()
            myInput.close()

        }

    @Throws(IOException::class)
    private fun copyFile(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
    }

}