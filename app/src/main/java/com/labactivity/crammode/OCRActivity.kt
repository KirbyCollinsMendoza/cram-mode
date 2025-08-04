package com.labactivity.crammode

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.labactivity.crammode.utils.FlashcardUtils
import com.labactivity.crammode.QuizUtils
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.activity.result.ActivityResultLauncher
import android.text.method.ScrollingMovementMethod
import android.widget.Scroller
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import com.labactivity.crammode.network.CohereClient




class OCRActivity : AppCompatActivity() {

    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnSummarize: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopy: Button

    private lateinit var ocrResult: EditText
    private lateinit var txtSummary: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerLength: Spinner
    private lateinit var spinnerFormat: Spinner
    private lateinit var spinnerFlashcardCount: Spinner
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var spinnerQuizCount: Spinner
    private lateinit var spinnerTimePerQuestion: Spinner
    private lateinit var imagePreviewList: LinearLayout

    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var docxPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var spinnerLanguage: Spinner

    private var selectedLanguage = "English"


    private var selectedImageUri: Uri? = null
    private var currentRotation = 0f
    private val imageCropQueue = ArrayDeque<Uri>()


    private val selectedImageUris = mutableListOf<Uri>()
    private val ocrResultsList = mutableListOf<String>()
    private lateinit var btnAddToOcr: Button


    private lateinit var cropLauncher: ActivityResultLauncher<CropImageContractOptions>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var recognizedText: String = ""
    private var currentMode: Mode = Mode.SUMMARIZE

    enum class Mode { SUMMARIZE, FLASHCARDS, QUIZ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)


        // Bind views
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnSummarize = findViewById(R.id.btnSummarize)
        btnClear = findViewById(R.id.btnClear)
        btnCopy = findViewById(R.id.btnCopy)
        ocrResult = findViewById(R.id.ocrResult)
        txtSummary = findViewById(R.id.txtSummary)
        progressBar = findViewById(R.id.progressBar)
        spinnerLength = findViewById(R.id.spinnerLength)
        spinnerFormat = findViewById(R.id.spinnerFormat)
        spinnerFlashcardCount = findViewById(R.id.spinnerFlashcardCount)
        modeToggleGroup = findViewById(R.id.modeToggleGroup)
        spinnerQuizCount = findViewById(R.id.spinnerQuizCount)
        spinnerTimePerQuestion = findViewById(R.id.spinnerTimePerQuestion)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)





        ocrResult.setScroller(Scroller(this))
        ocrResult.movementMethod = ScrollingMovementMethod()
        ocrResult.isVerticalScrollBarEnabled = true
        ocrResult.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }


        imagePreviewList = findViewById(R.id.imagePreviewList)















        spinnerQuizCount.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("3", "5", "10")
        )


        spinnerLength.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Short", "Medium", "Long")
        )
        spinnerFormat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Paragraph", "Bullets")
        )
        spinnerFlashcardCount.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("3", "5", "10")
        )


        val timeOptions = listOf("Easy (30s)", "Medium (20s)", "Hard (10s)")
        val adapterTime = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimePerQuestion.adapter = adapterTime


        val ocrResult = findViewById<EditText>(R.id.ocrResult)
        val btnSelectFile = findViewById<Button>(R.id.btnSelectFile)

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        val mimeType = contentResolver.getType(it) ?: ""

                        when {
                            mimeType.contains("pdf") -> {
                                extractTextFromPdf(it)
                            }

                            mimeType.contains("officedocument.wordprocessingml") || uri.toString()
                                .endsWith(".docx") -> {
                                try {
                                    val file = uriToFile(it)
                                    val text = DocxTextExtractor.extractText(file)
                                    ocrResult.setText(text)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        this,
                                        "Failed to extract DOCX text.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            else -> {
                                Toast.makeText(this, "Unsupported file type.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }

        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(Intent.createChooser(intent, "Select PDF or DOCX file"))
        }


        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedLanguage = parent.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }


        // Toggle group selection
        modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.btnModeSummarize -> Mode.SUMMARIZE
                    R.id.btnModeFlashcards -> Mode.FLASHCARDS
                    R.id.btnModeQuiz -> Mode.QUIZ
                    else -> Mode.SUMMARIZE
                }
                updateModeUI()
            }
        }

        updateModeUI()

        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    data?.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            imageCropQueue.add(clipData.getItemAt(i).uri)
                        }
                        processNextCrop()
                    }

                    data?.data?.let { uri ->
                        imageCropQueue.add(uri)
                        processNextCrop()
                    }
                }
            }



        cropLauncher = registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                result.uriContent?.let { uri ->
                    selectedImageUri = uri
                    selectedImageUris.add(uri)
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    addImageToPreviewList(bitmap)
                    runTextRecognition(bitmap)
                }
            } else {
                Toast.makeText(this, "Crop failed: ${result.error?.message}", Toast.LENGTH_SHORT)
                    .show()
            }

            // ✅ Move to next image in the queue
            processNextCrop()
        }



        btnTakePhoto.setOnClickListener {
            val cropOptions = CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    imageSourceIncludeCamera = true,
                    imageSourceIncludeGallery = false
                )
            )
            cropLauncher.launch(cropOptions)
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImageLauncher.launch(Intent.createChooser(intent, "Select Pictures"))

        }

        btnSummarize.setOnClickListener {
            recognizedText = ocrResult.text.toString()
            if (recognizedText.isNotBlank()) {
                when (currentMode) {
                    Mode.SUMMARIZE -> summarizeText(recognizedText)
                    Mode.FLASHCARDS -> generateFlashcards(recognizedText)
                    Mode.QUIZ -> generateQuiz(recognizedText)
                }
            } else {
                txtSummary.text = "No text found to process."
            }
        }

        btnClear.setOnClickListener {
            ocrResult.setText("")
            txtSummary.text = ""

            imagePreviewList.removeAllViews()
            selectedImageUris.clear()
            ocrResultsList.clear()

        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("summary", txtSummary.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val file = File.createTempFile("temp", ".docx", cacheDir)
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }


    private fun extractTextFromPdf(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "temp.pdf")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val textResults = mutableListOf<String>()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        textResults.add(visionText.text)
                        ocrResult.setText(textResults.joinToString("\n\n"))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun addImageToPreviewList(bitmap: Bitmap) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // You can adjust height here
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        imageView.setOnClickListener {
            val tempUri = getImageUriFromBitmap(bitmap)
            val intent = Intent(this@OCRActivity, FullscreenImageActivity::class.java)
            intent.putExtra("imageUri", tempUri.toString())
            startActivity(intent)
        }

        imagePreviewList.addView(imageView)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "TempImage", null)
        return Uri.parse(path)
    }


    private fun processNextCrop() {
        if (imageCropQueue.isNotEmpty()) {
            val nextUri = imageCropQueue.removeFirst()
            val cropOptions = CropImageContractOptions(
                nextUri,
                CropImageOptions(guidelines = CropImageView.Guidelines.ON)
            )
            cropLauncher.launch(cropOptions)
        }
    }


    private fun updateModeUI() {
        val layoutSummaryOptions = findViewById<LinearLayout>(R.id.layoutSummaryOptions)
        val layoutFlashcardCount = findViewById<LinearLayout>(R.id.layoutFlashcardCount)
        val labelResult = findViewById<TextView>(R.id.labelResult)
        val resultContainer = findViewById<ScrollView>(R.id.resultContainer)
        val layoutQuizCount = findViewById<LinearLayout>(R.id.layoutQuizCount)
        val layoutQuizTime = findViewById<LinearLayout>(R.id.layoutQuizTime)

        when (currentMode) {
            Mode.SUMMARIZE -> {
                btnSummarize.text = "✨ Summarize Text"
                layoutSummaryOptions.visibility = View.VISIBLE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.VISIBLE
                resultContainer.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                btnCopy.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE

            }

            Mode.FLASHCARDS -> {
                btnSummarize.text = "🧠 Generate Flashcards"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.VISIBLE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopy.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE
            }

            Mode.QUIZ -> {
                btnSummarize.text = "📝 Generate Quiz"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopy.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.VISIBLE
                layoutQuizTime.visibility = View.VISIBLE
            }
        }
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                ocrResultsList.add(recognizedText)
                ocrResult.setText(ocrResultsList.joinToString("\n\n"))
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                ocrResult.setText("Failed to recognize text: ${e.message}")
                progressBar.visibility = View.GONE
            }
    }

    private fun summarizeText(text: String) {
        val length = spinnerLength.selectedItem.toString().lowercase()
        val format = spinnerFormat.selectedItem.toString().lowercase()

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val prompt = if (selectedLanguage == "Filipino") {
            """
    Ikaw ay isang AI study assistant. Buodin ang sumusunod na teksto sa wikang Filipino bilang paghahanda para sa pagsusulit. 
    Gamitin ang malinaw, maikli, at estrukturadong anyo ng buod. Iwasan ang mga hindi kinakailangang detalye. 
    Ituon lamang sa mga mahahalagang konsepto.

    Teksto:
    $text
    """.trimIndent()
        } else {
            """
    You are an AI study assistant helping students prepare for exams. Summarize the following academic text into a clear, structured, and efficient study guide. 
    Focus only on the most important concepts. Remove fluff, simplify ideas, and make it easy to review.

    Text:
    $text
    """.trimIndent()
        }


        val service = CohereClient.service
        val request = CohereRequest(prompt, length, format, "command-r-plus")

        service.summarize("Bearer ${BuildConfig.COHERE_API_KEY}", request)
            .enqueue(object : Callback<CohereResponse> {
                override fun onResponse(
                    call: Call<CohereResponse>,
                    response: Response<CohereResponse>
                ) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true

                    val summary = response.body()?.summary
                    if (!summary.isNullOrBlank()) {
                        txtSummary.text = summary
                        Toast.makeText(
                            this@OCRActivity,
                            "Summary generated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        txtSummary.text = "No summary returned."
                        Toast.makeText(
                            this@OCRActivity,
                            "Empty summary received",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<CohereResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                    txtSummary.text = "API call failed: ${t.message}"
                    Toast.makeText(
                        this@OCRActivity,
                        "Failed to generate summary: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }


    private fun generateFlashcards(text: String) {
        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val count = spinnerFlashcardCount.selectedItem.toString().toInt()

        val shortenedText = if (text.length > 3000) text.substring(0, 3000) else text

        Toast.makeText(this, "Generating $count flashcards...", Toast.LENGTH_SHORT).show()

        val startTime = System.currentTimeMillis()
        Log.d("FlashcardDebug", "Original text length: ${text.length}")
        Log.d("FlashcardDebug", "Shortened text length: ${shortenedText.length}")

        val prompt = if (selectedLanguage == "Filipino") {
            """
    Ikaw ay isang AI tutor na tumutulong sa mabilisang paghahanda sa pagsusulit. 
    Gumawa ng eksaktong $count flashcards mula sa sumusunod na akademikong teksto. 
    Ang bawat flashcard ay nasa format ng Tanong at Sagot. Ituon sa mga pangunahing konsepto, depinisyon, at impormasyon na mahalaga sa pagsusulit.

    Format:
    Q: Ano ang X?
    A: Ito ay Y.

    Ilabas lamang ang $count Q&A flashcards sa itaas na format. Iwasan ang dagdag na paliwanag o iba pang teksto.

    Teksto:
    $shortenedText
    """.trimIndent()
        } else {
            """
    You are an AI tutor helping students prepare for exams in limited time. 
    Create exactly $count flashcards from the following academic text. 
    Each flashcard must follow a clear Q&A format focused on definitions, key facts, and core ideas. 
    Keep each flashcard concise and useful for fast review.

    Format:
    Q: What is X?
    A: It is Y.

    Only output exactly $count Q&A flashcards in this format. No explanations or extra text.

    Text:
    $shortenedText
    """.trimIndent()
        }


        Log.d("FlashcardDebug", "Prompt length: ${prompt.length}")

        val service = CohereClient.service
        val request = FlashcardRequest(prompt = prompt)

        service.generateFlashcards("Bearer ${BuildConfig.COHERE_API_KEY}", request)
            .enqueue(object : Callback<FlashcardResponse> {
                override fun onResponse(
                    call: Call<FlashcardResponse>,
                    response: Response<FlashcardResponse>
                ) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true

                    val raw = response.body()?.generations?.firstOrNull()?.text?.trim() ?: ""
                    Log.d("FlashcardRawOutput", raw)

                    val flashcards = FlashcardUtils.parseFlashcards(raw).shuffled()

                    if (flashcards.size == count) {
                        Toast.makeText(
                            this@OCRActivity,
                            "Flashcards generated successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(this@OCRActivity, FlashcardViewerActivity::class.java)
                        intent.putExtra("flashcards", ArrayList(flashcards))
                        startActivity(intent)
                    } else {
                        Log.w(
                            "FlashcardDebug",
                            "Expected $count flashcards, but got ${flashcards.size}"
                        )
                        Toast.makeText(
                            this@OCRActivity,
                            "Parsed ${flashcards.size}/$count flashcards. Try again or improve input.",
                            Toast.LENGTH_LONG
                        ).show()
                        txtSummary.text = raw // Show raw for debugging
                    }
                }

                override fun onFailure(call: Call<FlashcardResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                    Toast.makeText(
                        this@OCRActivity,
                        "Flashcard generation failed: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("FlashcardError", "API call failed", t)
                }
            })
    }


    private fun generateQuiz(text: String) {
        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val count = spinnerQuizCount.selectedItem.toString().toInt()
        Toast.makeText(this, "Generating $count quiz questions...", Toast.LENGTH_SHORT).show()

        val shortenedText = if (text.length > 3000) text.substring(0, 3000) else text

        val startTime = System.currentTimeMillis()
        Log.d("FlashcardDebug", "Original text length: ${text.length}")
        Log.d("FlashcardDebug", "Shortened text length: ${shortenedText.length}")

        Log.d("QuizDebug", "Selected language: $selectedLanguage")


        val prompt = if (selectedLanguage == "Filipino") {
            """
    Ikaw ay isang AI quiz generator para sa mga estudyanteng nag-aaral para sa pagsusulit. 
    Gumawa ng $count multiple-choice na tanong batay sa sumusunod na akademikong teksto. 
    Ituon ang mga tanong sa pag-unawa ng mahahalagang konsepto, hindi lamang pag-alala ng detalye.

    Format:
    Tanong: ...
    A. ...
    B. ...
    C. ...
    D. ...
    Sagot: X

    Teksto:
    $shortenedText
    """.trimIndent()
        } else {
            """
    You are an AI quiz generator for students preparing for exams. Based on the following academic material, create $count multiple-choice questions. 
    Focus the questions on testing understanding of key concepts and facts — ideal for last-minute review.

    Format:
    Question: ...
    A. ...
    B. ...
    C. ...
    D. ...
    Answer: X

    Text:
    $shortenedText
    """.trimIndent()
        }


        Log.d("FlashcardDebug", "Prompt length: ${prompt.length}")

        val service = CohereClient.service
        val request = QuizRequest(prompt = prompt)

        service.generateQuiz("Bearer ${BuildConfig.COHERE_API_KEY}", request)
            .enqueue(object : Callback<QuizResponse> {
                override fun onResponse(
                    call: Call<QuizResponse>,
                    response: Response<QuizResponse>
                ) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true

                    if (!response.isSuccessful) {
                        Toast.makeText(
                            this@OCRActivity,
                            "API error: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                        txtSummary.text = "Error: ${response.message()}"
                        return
                    }

                    val raw = response.body()?.generations?.firstOrNull()?.text ?: ""
                    val questions = QuizUtils.parseQuizQuestions(raw).shuffled()
                    if (questions.isNotEmpty()) {
                        Toast.makeText(
                            this@OCRActivity,
                            "Quiz generated successfully!",
                            Toast.LENGTH_SHORT

                        ).show()




                        val intent = Intent(this@OCRActivity, QuizViewerActivity::class.java)
                        intent.putParcelableArrayListExtra("quizQuestions", ArrayList(questions))

                        val selectedTime = spinnerTimePerQuestion.selectedItem.toString()
                        val timeValue = when {
                            selectedTime.contains("Easy") -> "easy"
                            selectedTime.contains("Medium") -> "medium"
                            selectedTime.contains("Hard") -> "hard"
                            else -> "medium"
                        }

                        intent.putExtra("timePerQuestion", timeValue)
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this@OCRActivity,
                            "Failed to parse quiz questions.",
                            Toast.LENGTH_LONG
                        ).show()
                        txtSummary.text = "No quiz questions generated."
                    }
                }

                override fun onFailure(call: Call<QuizResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                    Toast.makeText(
                        this@OCRActivity,
                        "Quiz generation failed: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }
}