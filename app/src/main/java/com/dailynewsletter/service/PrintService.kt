package com.dailynewsletter.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrintService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfService: PdfService,
    private val settingsRepository: com.dailynewsletter.data.repository.SettingsRepository
) {
    suspend fun printHtml(html: String, title: String) {
        val printerIp = settingsRepository.getPrinterIp()
        val printerEmail = settingsRepository.getPrinterEmail()

        val pdfFile = pdfService.htmlToPdf(html, title.replace(" ", "_"))

        when {
            !printerIp.isNullOrBlank() -> printViaIpp(pdfFile.absolutePath, printerIp, title)
            !printerEmail.isNullOrBlank() -> printViaEmail(pdfFile.absolutePath, printerEmail, title)
            else -> throw IllegalStateException("프린터가 설정되지 않았습니다. 설정에서 프린터 IP 또는 ePrint 이메일을 입력해주세요.")
        }
    }

    private suspend fun printViaIpp(pdfPath: String, printerIp: String, title: String) = withContext(Dispatchers.IO) {
        val url = URL("http://$printerIp:631/ipp/print")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/ipp")
            connection.doOutput = true

            val pdfBytes = java.io.File(pdfPath).readBytes()
            val ippRequest = buildIppPrintRequest(title, pdfBytes)

            connection.outputStream.use { out ->
                out.write(ippRequest)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("IPP 프린트 실패: HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildIppPrintRequest(title: String, pdfData: ByteArray): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()

        // IPP version 1.1
        buffer.write(byteArrayOf(0x01, 0x01))
        // Operation: Print-Job (0x0002)
        buffer.write(byteArrayOf(0x00, 0x02))
        // Request ID
        buffer.write(byteArrayOf(0x00, 0x00, 0x00, 0x01))

        // Operation attributes tag
        buffer.write(0x01)

        // charset
        writeIppAttribute(buffer, 0x47, "attributes-charset", "utf-8")
        // natural language
        writeIppAttribute(buffer, 0x48, "attributes-natural-language", "ko-KR")
        // printer URI
        writeIppAttribute(buffer, 0x45, "printer-uri", "ipp://localhost/ipp/print")
        // job name
        writeIppAttribute(buffer, 0x42, "job-name", title)
        // document format
        writeIppAttribute(buffer, 0x49, "document-format", "application/pdf")

        // End of attributes
        buffer.write(0x03)

        // Document data
        buffer.write(pdfData)

        return buffer.toByteArray()
    }

    private fun writeIppAttribute(buffer: java.io.ByteArrayOutputStream, tag: Int, name: String, value: String) {
        buffer.write(tag)
        // Name length
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        buffer.write(byteArrayOf((nameBytes.size shr 8).toByte(), nameBytes.size.toByte()))
        buffer.write(nameBytes)
        // Value length
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        buffer.write(byteArrayOf((valueBytes.size shr 8).toByte(), valueBytes.size.toByte()))
        buffer.write(valueBytes)
    }

    private suspend fun printViaEmail(pdfPath: String, printerEmail: String, title: String): Unit = withContext(Dispatchers.IO) {
        throw UnsupportedOperationException(
            "ePrint 이메일 프린트는 SMTP 설정이 필요합니다. " +
            "Wi-Fi 프린터 IP를 사용하거나, 앱 설정에서 SMTP 서버를 구성해주세요."
        )
    }
}
