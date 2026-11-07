package com.nullvariant.official

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private companion object {
        const val LIBRARY_FILE_NAME = "libexternal.so"
    }

    init {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private lateinit var loginButton: Button
    private lateinit var librarySpinner: Spinner
    private lateinit var delaySpinner: Spinner
    private lateinit var loaderPreferences: LoaderPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioAuto: RadioButton
    private lateinit var radioManual: RadioButton
    private lateinit var rootStatusView: TextView
    private lateinit var appBanner: ImageView
    private lateinit var mainScroll: View
    private lateinit var libraryDrawer: View
    private lateinit var drawerScrim: View
    private lateinit var librarySearch: EditText
    private lateinit var libraryList: LinearLayout
    private lateinit var libraryCount: TextView
    private lateinit var actionStatus: TextView

    private val commandRunner = CommandRunner()
    private val catalogClient by lazy { LibraryCatalogClient(assets) }
    private val assetExtractor by lazy { AssetExtractor(assets, cacheDir) }
    private var isReadyToInject: Boolean = false
    private var selectedLibrary = SelectedLibrary()
    private var delaySeconds: Int = 0
    private var showTestLibraries = false
    private val allLibraries = mutableListOf<JSONObject>()
    private val filteredLibraries = mutableListOf<JSONObject>()
    private var isDrawerOpen = false

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Fonts.applyMedieval(this, findViewById(R.id.root_container))

        mainScroll = findViewById(R.id.main_scroll)
        libraryDrawer = findViewById(R.id.library_drawer)
        drawerScrim = findViewById(R.id.drawer_scrim)
        librarySearch = findViewById(R.id.library_search)
        libraryList = findViewById(R.id.library_list)
        libraryCount = findViewById(R.id.library_count)
        setupLibraryDrawer()

        val appCacheDir = cacheDir.absolutePath
        val outputFile = File(appCacheDir, LIBRARY_FILE_NAME)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        progressBar = findViewById(R.id.progressBar)
        librarySpinner = findViewById(R.id.my_spinner)
        delaySpinner = findViewById(R.id.delay_spinner)
        loginButton = findViewById(R.id.login_button)
        radioGroup = findViewById(R.id.radioGroup)
        radioAuto = findViewById(R.id.radio_auto)
        radioManual = findViewById(R.id.radio_manual)
        rootStatusView = findViewById(R.id.root_status)
        appBanner = findViewById(R.id.app_banner)
        actionStatus = findViewById(R.id.action_status)

        ImageLoader.load(appBanner, R.drawable.nullvariant_thumbnail, dp(96))

        loaderPreferences = LoaderPreferences(this)

        val savedMode = loaderPreferences.getMode()
        if (savedMode == "auto") {
            radioAuto.isChecked = true
        } else {
            radioManual.isChecked = true
        }

        setupDelaySpinner()

        val savedDelay = loaderPreferences.getDelaySeconds()
        delaySpinner.setSelection(savedDelay)

        delaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                delaySeconds = position
                loaderPreferences.saveDelaySeconds(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radio_auto) "auto" else "manual"
            loaderPreferences.saveMode(mode)
        }

        loginButton.setOnClickListener {
            if (!isReadyToInject) {
                loginButton.isEnabled = false
                loginButton.text = "PREPARING..."
                progressBar.visibility = View.VISIBLE
                actionStatus.text = "PREPARING ENGINE..."
                librarySpinner.isEnabled = false
                delaySpinner.isEnabled = false
                radioGroup.isEnabled = false
                proceedWithDownload()
            } else {
                injectLibrary(LIBRARY_FILE_NAME)
                showToast("Injecting, please wait...")
                loginButton.isEnabled = false
                progressBar.visibility = View.VISIBLE
                actionStatus.text = "INJECTING..."
                radioGroup.isEnabled = false
                delaySpinner.isEnabled = false
            }
        }

        findViewById<View>(R.id.telegram_button).setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/toolsdropdaw")))
            }.onFailure {
                showToast("Unable to open Telegram")
            }
        }

        appBanner.setOnLongClickListener {
            appBanner.alpha = 0.7f
            Handler(Looper.getMainLooper()).postDelayed({
                appBanner.alpha = 1.0f
            }, 500)

            filterLibraries(!showTestLibraries)

            val message = if (showTestLibraries) {
                "Test libraries shown"
            } else {
                "Test libraries hidden"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            true
        }

        loadLibraryCatalog()
        updateRootStatus()
    }

    private fun setupDelaySpinner() {
        val delayOptions = arrayOf("NO DELAY", "1S DELAY", "2S DELAY", "3S DELAY", "4S DELAY", "5S DELAY")
        delaySpinner.adapter = SimpleSpinnerAdapter(this, delayOptions)
    }

    private fun applyLibrarySelection(position: Int) {
        if (position !in filteredLibraries.indices) return

        val selectedLibraryJson = filteredLibraries[position]
        handleSelectedJsonData(selectedLibraryJson)
    }

    private fun loadLibraryCatalog() {
        lifecycleScope.launch {
            val root = catalogClient.load().getOrElse { _ ->
                Toast.makeText(this@MainActivity, "Failed to load servers data", Toast.LENGTH_SHORT).show()
                return@launch
            }

            runCatching {
                val dataArray = root.getJSONArray("DATA")
                allLibraries.clear()
                for (i in 0 until dataArray.length()) {
                    allLibraries.add(dataArray.getJSONObject(i))
                }
            }.onFailure { _ ->
                Toast.makeText(this@MainActivity, "Failed to load servers data", Toast.LENGTH_SHORT).show()
                return@launch
            }

            refreshLibraryDrawer()
            filterLibraries(false)
        }
    }

    private fun setupLibraryDrawer() {
        libraryDrawer.post {
            libraryDrawer.translationX = -libraryDrawer.width.toFloat()
        }

        findViewById<View>(R.id.library_menu_button).setOnClickListener {
            setLibraryDrawerOpen(!isDrawerOpen)
        }
        findViewById<View>(R.id.drawer_close).setOnClickListener {
            setLibraryDrawerOpen(false)
        }
        drawerScrim.setOnClickListener {
            setLibraryDrawerOpen(false)
        }

        librarySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                refreshLibraryDrawer(text?.toString().orEmpty())
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        })

        findViewById<View>(R.id.drawer_about).setOnClickListener {
            setLibraryDrawerOpen(false)
            AboutDialog(this).show()
        }

        refreshLibraryDrawer()
    }

    private fun setLibraryDrawerOpen(open: Boolean) {
        if (isDrawerOpen == open) return
        isDrawerOpen = open

        if (open) {
            drawerScrim.visibility = View.VISIBLE
            drawerScrim.alpha = 0f
            drawerScrim.animate().alpha(1f).setDuration(180L).start()

            libraryDrawer.visibility = View.VISIBLE
            libraryDrawer.animate().cancel()
            libraryDrawer.animate()
                .translationX(0f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            setMainContentBlur(true)
        } else {
            libraryDrawer.animate().cancel()
            libraryDrawer.animate()
                .translationX(-libraryDrawer.width.toFloat())
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { libraryDrawer.visibility = View.GONE }
                .start()

            drawerScrim.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { drawerScrim.visibility = View.GONE }
                .start()
            setMainContentBlur(false)
        }
    }

    @Suppress("NewApi")
    private fun setMainContentBlur(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        mainScroll.setRenderEffect(
            if (enabled) {
                RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
    }

    private fun refreshLibraryDrawer(query: String = librarySearch.text?.toString().orEmpty()) {
        if (!::libraryList.isInitialized) return

        val normalizedQuery = query.trim()
        val visibleLibraries = allLibraries.filter { library ->
            normalizedQuery.isBlank() ||
                library.optString("NAME").contains(normalizedQuery, ignoreCase = true) ||
                library.optString("GAME").contains(normalizedQuery, ignoreCase = true) ||
                library.optString("TYPE").contains(normalizedQuery, ignoreCase = true)
        }

        libraryList.removeAllViews()
        libraryCount.text = if (allLibraries.isEmpty()) {
            "LOADING LIBRARIES..."
        } else {
            "${visibleLibraries.size} AVAILABLE LIBRARIES"
        }

        if (visibleLibraries.isEmpty()) {
            val emptyState = TextView(this).apply {
                text = if (allLibraries.isEmpty()) "Loading libraries..." else "No matching library"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.loader_text_muted))
                textSize = 11f
                setPadding(12, 18, 12, 18)
            }
            libraryList.addView(emptyState)
            return
        }

        visibleLibraries.forEach { library ->
            val row = layoutInflater.inflate(R.layout.item_library_drawer, libraryList, false)
            val name = library.optString("NAME").trim().ifBlank { "Unnamed library" }
            val packageName = library.optString("GAME").trim()
            val version = LibraryMetadata.versionLabel(library)
            val gameLabel = LibraryMetadata.gameLabel(library)
            val meta = listOf(gameLabel, version).filter { it.isNotBlank() }.joinToString(" • ")
            val status = LibraryMetadata.status(library)
            val available = status == "ACTIVE"

            row.findViewById<TextView>(R.id.drawer_name)?.text = name
            row.findViewById<TextView>(R.id.drawer_meta)?.text = meta
            row.findViewById<TextView>(R.id.drawer_package)?.apply {
                text = packageName
                visibility = if (packageName.isBlank()) View.GONE else View.VISIBLE
            }
            row.findViewById<TextView>(R.id.drawer_status)?.apply {
                text = status
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (available) R.color.loader_status_active else R.color.loader_status_maintenance
                    )
                )
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    if (available) R.drawable.status_pill else R.drawable.maintenance_pill
                )
            }
            row.background = ContextCompat.getDrawable(
                this,
                if (available && name == selectedLibrary.name) {
                    R.drawable.drawer_item_selected
                } else {
                    R.drawable.drawer_item
                }
            )
            row.isEnabled = available
            row.alpha = if (available) 1f else 0.58f

            row.findViewById<ImageView>(R.id.drawer_logo)
                ?.let { ImageLoader.load(it, R.drawable.nullvariant_bubble, dp(48)) }

            row.setOnClickListener {
                if (available) {
                    selectLibraryFromDrawer(library)
                } else {
                    showToast("$name is under maintenance")
                }
            }
            libraryList.addView(row)
        }
    }

    private fun selectLibraryFromDrawer(library: JSONObject) {
        if (!LibraryMetadata.isActive(library)) {
            showToast("${library.optString("NAME", "Library")} is under maintenance")
            return
        }
        handleSelectedJsonData(library)
        val filteredPosition = filteredLibraries.indexOfFirst {
            it.optString("NAME") == library.optString("NAME")
        }
        if (filteredPosition >= 0) {
            librarySpinner.setSelection(filteredPosition, false)
        }
        saveSelectedLibrary(selectedLibrary.name)
        setLibraryDrawerOpen(false)
    }

    private fun filterLibraries(includeTestLibraries: Boolean) {
        showTestLibraries = includeTestLibraries
        filteredLibraries.clear()
        if (includeTestLibraries) {
            filteredLibraries.addAll(allLibraries.filter { library ->
                library.optString("NAME").contains("TEST", ignoreCase = true) &&
                    LibraryMetadata.isActive(library)
            })
        } else {
            filteredLibraries.addAll(allLibraries.filter { library ->
                !library.optString("NAME").contains("TEST", ignoreCase = true) &&
                    LibraryMetadata.isActive(library)
            })
        }
        updateSpinnerWithFilteredData()
    }

    private fun updateSpinnerWithFilteredData() {
        val nameList = mutableListOf<String>()

        for (item in filteredLibraries) {
            nameList.add(item.optString("NAME"))
        }

        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, nameList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    view = inflater.inflate(R.layout.spinner_item, parent, false)
                }
                val nameTextView = view!!.findViewById<TextView>(R.id.spinnerText)
                val logoImageView = view.findViewById<ImageView>(R.id.logoImage)
                nameTextView?.text = " ${nameList[position]}"
                logoImageView?.setImageResource(R.drawable.nullvariant_bubble)

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return getView(position, convertView, parent)
            }
        }

        librarySpinner.adapter = adapter

        librarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View?, position: Int, id: Long) {
                applyLibrarySelection(position)
            }
            override fun onNothingSelected(parentView: AdapterView<*>) {
            }
        }

        val savedLibraryName = loaderPreferences.getSelectedLibrary()
        val savedPosition = filteredLibraries.indexOfFirst {
            it.optString("NAME") == savedLibraryName
        }
        val initialPosition = if (savedPosition >= 0) savedPosition else 0

        if (filteredLibraries.isNotEmpty()) {
            librarySpinner.setSelection(initialPosition, false)
            val initialName = filteredLibraries[initialPosition].optString("NAME")
            if (selectedLibrary.name != initialName || selectedLibrary.connectUrl.isBlank()) {
                applyLibrarySelection(initialPosition)
            }
        }
    }

    fun handleSelectedJsonData(jsonObject: JSONObject) {
        selectedLibrary = SelectedLibrary.fromJson(jsonObject)
        val maintenance = !LibraryMetadata.isActive(jsonObject)
        findViewById<TextView>(R.id.library_name)?.text = selectedLibrary.name
        findViewById<TextView>(R.id.library_game)?.text = LibraryMetadata.gameLabel(jsonObject)
        findViewById<TextView>(R.id.library_version)?.apply {
            text = LibraryMetadata.versionLabel(jsonObject)
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.library_package)?.apply {
            text = selectedLibrary.gamePackage
            visibility = if (selectedLibrary.gamePackage.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.library_status)?.apply {
            text = if (maintenance) "● Maintenance" else "● Ready"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (maintenance) R.color.loader_status_maintenance else R.color.loader_status_active
                )
            )
            background = ContextCompat.getDrawable(
                this@MainActivity,
                if (maintenance) R.drawable.maintenance_pill else R.drawable.status_pill
            )
        }
        if (maintenance) {
            isReadyToInject = false
            loginButton.text = "UNAVAILABLE"
            loginButton.isEnabled = false
        } else {
            loginButton.isEnabled = true
            if (!isReadyToInject) loginButton.text = "PREPARE"
        }
        ImageLoader.load(appBanner, R.drawable.nullvariant_thumbnail, dp(96))
        appBanner.animate().cancel()
        appBanner.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180L).withEndAction {
            appBanner.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
        }.start()

        refreshLibraryDrawer()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveSelectedLibrary(libraryName: String) {
        loaderPreferences.saveSelectedLibrary(libraryName)
    }

    private fun resetLoginState(message: String) {
        showToast(message)
        progressBar.visibility = View.GONE
        actionStatus.text = message
        loginButton.text = "PREPARE"
        loginButton.isEnabled = true
        librarySpinner.isEnabled = true
        delaySpinner.isEnabled = true
        radioGroup.isEnabled = true
        isReadyToInject = false
    }

    private fun proceedWithDownload() {
        lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) {
                assetExtractor.extractAll()
                isLibraryReady()
            }
            if (!ready) {
                resetLoginState("FAILED TO LOAD RUNTIME LIBRARY")
                isReadyToInject = false
                return@launch
            }
            delay(250)
            progressBar.visibility = View.GONE
            loginButton.text = "INJECT"
            actionStatus.text = "READY TO INJECT"
            showToast("Ready to inject")
            loginButton.isEnabled = true
            radioGroup.isEnabled = true
            delaySpinner.isEnabled = true
            isReadyToInject = true
            animateReadyButton()
        }
    }

    private fun animateReadyButton() {
        loginButton.animate().cancel()
        loginButton.animate().scaleX(1.04f).scaleY(1.04f).setDuration(160L).withEndAction {
            loginButton.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
        }.start()
    }

    private fun isLibraryReady(): Boolean {
        val libraryFile = File(cacheDir, LIBRARY_FILE_NAME)
        return libraryFile.isFile && libraryFile.length() > 0L
    }

    private fun injectLibrary(libraryFileName: String) {
        val cacheDirectory = File(cacheDir.absolutePath)
        val outputFile = File(cacheDirectory, libraryFileName)
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            resetLoginState("FAILED TO LOAD RUNTIME LIBRARY")
            return
        }

        cacheDirectory.setReadable(true, false)
        cacheDirectory.setWritable(true, false)
        cacheDirectory.setExecutable(true, false)
        cacheDirectory.listFiles()?.forEach { it.setExecutable(true, false) }

        lifecycleScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { commandRunner.hasRootAccess() }
            val injectionCommand = InjectionCommandBuilder.build(
                cacheDirectory = cacheDirectory,
                library = selectedLibrary,
                libraryFileName = libraryFileName,
                hasRootAccess = hasRoot
            )
            if (radioAuto.isChecked) {
                launchGame(selectedLibrary.gamePackage)
                delay(1000)
                delay(delaySeconds * 1000L)
            }
            val success = withContext(Dispatchers.IO) {
                commandRunner.run(injectionCommand, hasRoot)
            }
            finishInjection(success)
        }
    }

    private fun finishInjection(success: Boolean) {
        progressBar.visibility = View.GONE
        actionStatus.text = if (success) "INJECTION COMPLETE" else "INJECTION FAILED"
        showToast(if (success) "Injection completed" else "Injection failed")
        loginButton.text = "RE-INJECT"
        loginButton.isEnabled = true
        librarySpinner.isEnabled = true
        delaySpinner.isEnabled = true
        radioGroup.isEnabled = true
    }

    private fun launchGame(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                true
            } else {
                Toast.makeText(this, "Game not installed!", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start game!", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun updateRootStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val isRooted = commandRunner.hasRootAccess()
            withContext(Dispatchers.Main) {
                rootStatusView.text = if (isRooted) "ROOTED" else "ROOTLESS"
                rootStatusView.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isRooted) R.color.loader_status_active else R.color.loader_text_muted
                    )
                )
                rootStatusView.visibility = View.VISIBLE
            }
        }
    }

    private fun cleanUp() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        LibraryDownloader.cancelNotification(this)
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            cleanUp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUp()
    }
}
