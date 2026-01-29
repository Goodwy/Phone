package dev.goodwy.phone.activities

import android.annotation.SuppressLint
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.behaviorule.arturdumchev.library.pixels
import com.behaviorule.arturdumchev.library.setHeight
import com.goodwy.commons.dialogs.ColorPickerDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.RadioGroupIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import dev.goodwy.phone.BuildConfig
import dev.goodwy.phone.R
import dev.goodwy.phone.databinding.ActivitySettingsDialpadBinding
import dev.goodwy.phone.extensions.*
import dev.goodwy.phone.helpers.*
import dev.goodwy.phone.models.RecentCall
import dev.goodwy.phone.models.SpeedDial
import com.google.gson.Gson
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import java.io.InputStreamReader
import java.util.*
import kotlin.math.abs

class SettingsDialpadActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsDialpadBinding::inflate)

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    private var speedDialValues = ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val hideDialpadHandler = Handler(Looper.getMainLooper())


    @SuppressLint("MissingSuperCall", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
//            setupEdgeToEdge(
//                padBottomSystem = listOf(
//                    dialpadWrapper.dialpadLayout,
//                ),
//            )
            setupMaterialScrollListener(binding.dialpadNestedScrollview, binding.dialpadAppbar)
        }

        updateDialpadSize()
        updateDialpadBottomMargin()
        updateCallButtonSize()

        if (config.hideDialpadNumbers) {
            binding.dialpadWrapper.apply {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                //dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }
        }

        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        val properTextColor = getProperTextColor()
        val properBackgroundColor = getProperBackgroundColor()
        val surfaceColor = getSurfaceColor()

        binding.apply {
            setupTopAppBar(dialpadAppbar, NavigationIcon.Arrow)

            arrayOf(
                styleHolder,
                dialpadSettingsCardHolder,
                dialpadSizeCardHolder
            ).forEach {
                it.setCardBackgroundColor(surfaceColor)
            }

            speedDialValues = config.getSpeedDialValues()
            initStyle()
            updateTextColors(dialpadSettingsHolder)

            arrayOf(
                toneVolumeMinus, toneVolumePlus,
                dialpadScaleMinus, dialpadScalePlus,
                dialpadSizeMinus, dialpadSizePlus,
                dialpadBottomMarginMinus, dialpadBottomMarginPlus,
                buttonSizeMinus, buttonSizePlus,
                buttonSecondSizeMinus, buttonSecondSizePlus,
            ).forEach {
                it.applyColorFilter(properTextColor)
            }

            val properPrimaryColor = getProperPrimaryColor()
            arrayOf(
                settingsGeneralLabel,
                settingsDialpadSizeLabel
            ).forEach {
                it.setTextColor(properPrimaryColor)
            }

            val onBackground = properBackgroundColor.getContrastColor()
            val buttonBackground = onBackground.adjustAlpha(0.2f)
            arrayOf(
                toneVolumeButtons, dialpadScaleButtons, dialpadSizeButtons, dialpadBottomMarginButtons,
                buttonSizeButtons, buttonSecondSizeButtons
            ).forEach {
                it.background.applyColorFilter(buttonBackground)
            }
            arrayOf(
                toneVolumeDivider, dialpadSizeDivider, dialpadBottomMarginDivider,
                buttonSizeDivider, buttonSecondSizeDivider
            ).forEach {
                it.background.applyColorFilter(properTextColor)
            }
        }

        setupPurchaseThankYou()

//        setupDialpadStyle()
        setupButtonsColorList()
        setupPrimarySimCard()
        setupHideDialpadLetters()
        setupShowVoicemailIcon()
        setupDialpadSecondaryLanguage()
        setupDialpadSecondaryTypeface()
        setupDialpadHashtagLongClick()
        setupClearDialpad()
        setupDialpadVibrations()
        setupDialpadBeeps()
        setupToneVolume()

        setupDialpadScale()
        setupDialpadSize()
        setupDialpadBottomMargin()
        setupButtonSize()

        val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
        val subList: ArrayList<String> =
            arrayListOf(
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        val ruStoreList: ArrayList<String> =
            arrayListOf(
                productIdX1, productIdX2, productIdX3,
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        PurchaseHelper().checkPurchase(
            this@SettingsDialpadActivity,
            iapList = iapList,
            subList = subList,
            ruStoreList = ruStoreList
        ) { updatePro ->
            if (updatePro) updatePro()
        }
    }

    override fun onRestart() {
        super.onRestart()
        speedDialValues = config.getSpeedDialValues()
    }

    private fun initStyle() {
        binding.apply {

            val surfaceColor =
                if (isDynamicTheme() && !isSystemInDarkMode()) getProperBackgroundColor() else getSurfaceColor()

            dialpadWrapper.apply {
                dialpadVoicemail.beVisibleIf(config.showVoicemailIcon && !config.hideDialpadLetters)
                dialpadHolder.setBackgroundColor(surfaceColor)

                dialpadHolder.alpha = 0.7f
                dialpadCallButton.background.applyColorFilter(config.simIconsColors[1])

                dialpadBottomMargin.apply {
                    setBackgroundColor(surfaceColor)
                    setHeight(100)
                }
            }
            updateDialpadButton()
            initLetters()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initLetters() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val getProperTextColor = getProperTextColor()

        binding.dialpadWrapper.apply {
            if (config.hideDialpadLetters) {
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters,
                    dialpad1Letters
                ).forEach {
                    it.beGone()
                }
            } else {
                dialpad1Letters.apply {
                    beInvisible()
                    setTypeface(null, config.dialpadSecondaryTypeface)
                }
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beVisible()
                    it.setTypeface(null, config.dialpadSecondaryTypeface)
                }


                if (!DialpadT9.Initialized) {
                    val reader = InputStreamReader(resources.openRawResource(R.raw.t9languages))
                    DialpadT9.readFromJson(reader.readText())
                }
                val langPref = config.dialpadSecondaryLanguage
                val langLocale = Locale.getDefault().language
                val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
                if (langPref!! != LANGUAGE_NONE && langPref != LANGUAGE_SYSTEM || isAutoLang) {
                    val lang = if (isAutoLang) langLocale else langPref
                    dialpad1Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad2Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad3Letters.text = DialpadT9.getLettersForNumber(3, lang) + "\nDEF"
                    dialpad4Letters.text = DialpadT9.getLettersForNumber(4, lang) + "\nGHI"
                    dialpad5Letters.text = DialpadT9.getLettersForNumber(5, lang) + "\nJKL"
                    dialpad6Letters.text = DialpadT9.getLettersForNumber(6, lang) + "\nMNO"
                    dialpad7Letters.text = DialpadT9.getLettersForNumber(7, lang) + "\nPQRS"
                    dialpad8Letters.text = DialpadT9.getLettersForNumber(8, lang) + "\nTUV"
                    dialpad9Letters.text = DialpadT9.getLettersForNumber(9, lang) + "\nWXYZ"

                    val fontSizeRu = getTextSize() - 16f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                    }
                } else {
                    dialpad1Letters.text = "ABC"
                    dialpad2Letters.text = "ABC"
                    dialpad3Letters.text = "DEF"
                    dialpad4Letters.text = "GHI"
                    dialpad5Letters.text = "JKL"
                    dialpad6Letters.text = "MNO"
                    dialpad7Letters.text = "PQRS"
                    dialpad8Letters.text = "TUV"
                    dialpad9Letters.text = "WXYZ"

                    val fontSize = getTextSize() - 8f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            if (areMultipleSIMsAvailable) {
                dialpadHide.applyColorFilter(getProperTextColor)
                dialpadChangeSim.apply {
                    applyColorFilter(getProperTextColor)
                    beVisible()
                }
                updateCallButton()
            } else {
                dialpadHide.applyColorFilter(getProperTextColor)
                dialpadChangeSim.beGone()
                val color = config.simIconsColors[1]
                val callIcon = resources.getColoredDrawableWithColor(
                    this@SettingsDialpadActivity,
                    R.drawable.ic_phone_vector,
                    color.getContrastColor()
                )
                dialpadCallIcon.setImageDrawable(callIcon)
                dialpadCallButton.background.applyColorFilter(color)
            }

            dialpadClearChar.applyColorFilter(Color.GRAY)
            dialpadClearChar.alpha = 0.4f
            dialpadClearCharX.applyColorFilter(getProperTextColor)

            dialpadMenu.applyColorFilter(getProperTextColor)
        }
    }

    private fun updateCallButton() {
        val oneSim = config.currentSIMCardIndex == 0
        val simColor = if (oneSim) config.simIconsColors[1] else config.simIconsColors[2]
        val callIconId = if (oneSim) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
        val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, callIconId, simColor.getContrastColor())
        binding.dialpadWrapper.dialpadCallIcon.setImageDrawable(callIcon)
        binding.dialpadWrapper.dialpadCallButton.background.applyColorFilter(simColor)
    }

    private fun updateDialpadButton() {
        val buttonColor = getDialpadButtonsColor()
        val textColor = buttonColor.getContrastColor()

        when (config.dialpadButtonsColorStyle) {
            DIALPAD_BUTTONS_STYLE_COLOR -> {
                binding.settingsDialpadButtonColorDefault.beGone()
                binding.settingsDialpadButtonColor.beVisible()
                binding.settingsDialpadButtonColor.setFillWithStroke(buttonColor, buttonColor)
            }

            DIALPAD_BUTTONS_STYLE_TRANSPARENT -> {
                binding.settingsDialpadButtonColor.beGone()
                binding.settingsDialpadButtonColorDefault.beVisible()
                binding.settingsDialpadButtonColorDefault.text = getString(R.string.transparent_color)
            }

            else -> {
                binding.settingsDialpadButtonColor.beGone()
                binding.settingsDialpadButtonColorDefault.beVisible()
                binding.settingsDialpadButtonColorDefault.text = getString(R.string.default_color)
            }
        }

        binding.dialpadWrapper.apply {
            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder,
                dialpad5Holder, dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder,
                dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background.applyColorFilter(buttonColor)
            }

            arrayOf(
                dialpad1, dialpad2, dialpad3, dialpad4, dialpad5,
                dialpad6, dialpad7, dialpad8, dialpad9, dialpad0,
                dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters,
                dialpadPlus
            ).forEach {
                it.setTextColor(textColor)
            }

            arrayOf(
                dialpadVoicemail, dialpadAsterisk, dialpadHashtag
            ).forEach {
                it.applyColorFilter(textColor)
            }
        }
    }

    private fun setupDialpadScale() {
        binding.apply {
            val progress = (config.dialpadScale * 100).toInt()
            dialpadScale.progress = progress
            val textProgress = "$progress %"
            dialpadScaleValue.text = textProgress

            dialpadScale.min = 85

            dialpadScaleMinus.setOnClickListener {
                dialpadScale.progress -= 1
                showDialpad()
            }
            dialpadScaleValue.setOnClickListener {
                dialpadScale.progress = 96
                showDialpad()
            }
            dialpadScalePlus.setOnClickListener {
                dialpadScale.progress += 1
                showDialpad()
            }

            dialpadScale.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    dialpadWrapper.root.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    dialpadWrapper.root.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val textScale = "$progress %"
                    val scale = progress.toFloat() / 100
                    binding.dialpadScaleValue.text = textScale

                    updateDialpadSize(scale = scale)
                    config.dialpadScale = scale
                }
            })
        }
    }

    private fun setupDialpadSize() {
        binding.apply {
            val progress = config.dialpadSize
            dialpadSize.progress = progress
            val textProgress = "$progress %"
            dialpadSizeValue.text = textProgress

            dialpadSize.min = 50

            dialpadSizeMinus.setOnClickListener {
                dialpadSize.progress -= 1
                showDialpad()
            }
            dialpadSizeValue.setOnClickListener {
                dialpadSize.progress = 100
                showDialpad()
            }
            dialpadSizePlus.setOnClickListener {
                dialpadSize.progress += 1
                showDialpad()
            }

            dialpadSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    dialpadWrapper.root.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    dialpadWrapper.root.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateDialpadSize(height = progress)
                    config.dialpadSize = progress
                }
            })
        }
    }

    private fun setupDialpadBottomMargin() {
        binding.apply {
            val progress = config.dialpadBottomMargin
            dialpadBottomMarginPref.progress = progress
            val textProgress = "+$progress pixels"
            dialpadBottomMarginValue.text = textProgress

            dialpadBottomMarginPref.min = 0

            dialpadBottomMarginMinus.setOnClickListener {
                dialpadBottomMarginPref.progress -= 1
                showDialpad()
            }
            dialpadBottomMarginValue.setOnClickListener {
                dialpadBottomMarginPref.progress = 0
                showDialpad()
            }
            dialpadBottomMarginPlus.setOnClickListener {
                dialpadBottomMarginPref.progress += 1
                showDialpad()
            }

            dialpadBottomMarginPref.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    dialpadWrapper.root.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    dialpadWrapper.root.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateDialpadBottomMargin(progress)
                    config.dialpadBottomMargin = progress
                }
            })
        }
    }

    private fun setupButtonSize() {
        binding.apply {
            if (checkPro()) {
                arrayOf(
                    buttonSizeHolder, buttonSize, buttonSecondSizeHolder, buttonSecondSize
                ).forEach {
                    it.alpha = 1f
                }
                buttonSizeLabel.setText(R.string.button_primary)
                buttonSecondSizeLabel.setText(R.string.button_secondary)
            } else {
                arrayOf(
                    buttonSizeHolder, buttonSize, buttonSecondSizeHolder, buttonSecondSize
                ).forEach {
                    it.alpha = 0.4f
                }
                val lockText = addLockedLabelIfNeeded(R.string.button_primary)
                buttonSizeLabel.text = lockText
                val lockTextSecond = addLockedLabelIfNeeded(R.string.button_secondary)
                buttonSecondSizeLabel.text = lockTextSecond
            }

            val progress = config.callButtonPrimarySize
            buttonSize.progress = progress
            val textProgress = "$progress %"
            buttonSizeValue.text = textProgress

            buttonSize.min = 50

            buttonSizeMinus.setOnClickListener {
                buttonSize.progress -= 1
                showDialpad()
            }
            buttonSizeValue.setOnClickListener {
                buttonSize.progress = 100
                showDialpad()
            }
            buttonSizePlus.setOnClickListener {
                buttonSize.progress += 1
                showDialpad()
            }

            buttonSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    dialpadWrapper.root.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    dialpadWrapper.root.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateCallButtonSize(progress)
                    config.callButtonPrimarySize = progress
                }
            })

            //second button
            if (areMultipleSIMsAvailable()) {
//                buttonSecondSizeHolder.beVisible()
//                buttonSecondSize.beVisible()
                val progressSecond = config.callButtonSecondarySize
                buttonSecondSize.progress = progressSecond
                val textProgressSecond = "$progressSecond %"
                buttonSecondSizeValue.text = textProgressSecond

                buttonSecondSize.min = 50

                buttonSecondSizeMinus.setOnClickListener {
                    buttonSecondSize.progress -= 1
                    showDialpad()
                }
                buttonSecondSizeValue.setOnClickListener {
                    buttonSecondSize.progress = 100
                    showDialpad()
                }
                buttonSecondSizePlus.setOnClickListener {
                    buttonSecondSize.progress += 1
                    showDialpad()
                }

                buttonSecondSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                        dialpadWrapper.root.beVisible()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        dialpadWrapper.root.beGone()
                    }

                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        updateCallButtonSize(progress)
                        config.callButtonSecondarySize = progress
                    }
                })
            } else {
                buttonSecondSizeHolder.beGone()
                buttonSecondSize.beGone()
            }
        }
    }

    private fun updateDialpadSize(height: Int = config.dialpadSize, scale: Float = config.dialpadScale) {
        val dimens = pixels(R.dimen.dialpad_height)
        val dialpadHeight = (dimens * (height / 100f)).toInt()
        binding.dialpadWrapper.dialpadWrapper.setHeight(dialpadHeight)

        binding.dialpadWrapper.dialpadWrapper.scaleX = scale
        binding.dialpadWrapper.dialpadWrapper.scaleY = scale

        val defaultPadding = pixels(R.dimen.normal_margin)
        binding.dialpadWrapper.dialpadCallIcon.setPadding((defaultPadding * (height / 100f)).toInt())

        val textHeight = "$height %"
        binding.dialpadSizeValue.text = textHeight
    }

    private fun updateDialpadBottomMargin(margin: Int = config.dialpadBottomMargin) {
        val start = navigationBarHeight.toFloat()
        binding.dialpadWrapper.dialpadBottomMargin.setHeight((start + margin).toInt())

        val textPercent = "+$margin pixels"
        binding.dialpadBottomMarginValue.text = textPercent
    }

    private fun updateCallButtonSize(percent: Int) {
        val view = binding.dialpadWrapper.dialpadCallButton
        val margin = (view.width * ((100 - percent) / 100f)).toInt()

        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.let { params ->
            params.setMargins(margin, margin, margin, margin) // left, top, right, bottom
            view.layoutParams = params
        }
    }

    private fun updateCallButtonSize() {
        val size = config.callButtonPrimarySize
        val view = binding.dialpadWrapper.dialpadCallButton
        val margin = (view.width * ((100 - size) / 100f)).toInt()

        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.let { params ->
            params.setMargins(margin, margin, margin, margin) // left, top, right, bottom
            view.layoutParams = params
        }
    }

    private fun showDialpad() {
        binding.dialpadWrapper.root.beVisible()
        hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
        hideDialpadHandler.postDelayed(updateHideDialpadTask, 2000)
    }

    private val updateHideDialpadTask = Runnable {
        binding.dialpadWrapper.root.beGone()
    }

    private fun setupDialpadStyle() {
//        val pro = checkPro()
//        val iOS = addLockedLabelIfNeeded(R.string.ios_g, pro)
//        binding.settingsDialpadStyle.text = getDialpadStyleText()
//        binding.settingsDialpadStyleHolder.setOnClickListener {
//            val items = arrayListOf(
//                RadioItem(DIALPAD_ORIGINAL, getString(R.string.clean_theme_g)),
//                RadioItem(DIALPAD_GRID, getString(R.string.grid)),
//                RadioItem(DIALPAD_IOS, iOS),
//                RadioItem(DIALPAD_CONCEPT, getString(R.string.concept_theme_g))
//            )
//
//            RadioGroupDialog(
//                this@SettingsDialpadActivity,
//                items,
//                config.dialpadStyle,
//                R.string.theme,
//                defaultItemId = DIALPAD_ORIGINAL
//            ) {
//                if (it as Int == DIALPAD_IOS) {
//                    if (pro) {
//                        binding.dialpadClearWrapper.root.beGone()
//                        binding.dialpadRectWrapper.root.beGone()
//                        config.dialpadStyle = it
//                        binding.settingsDialpadStyle.text = getDialpadStyleText()
//                        initStyle()
//                        updateDialpadSize()
//                        updateDialpadBottomMargin()
//                        showDialpad()
//                    } else {
//                        shakePurchase()
//
//                        RxAnimation.from(binding.styleHolder)
//                            .shake(shakeTranslation = 2f)
//                            .subscribe()
//
//                        showSnackbar(binding.root)
//                    }
//                } else if (it == DIALPAD_CONCEPT) {
//                    binding.dialpadRoundWrapper.root.beGone()
//                    binding.dialpadClearWrapper.root.beGone()
//                    config.dialpadStyle = it
//                    binding.settingsDialpadStyle.text = getDialpadStyleText()
//                    initStyle()
//                    updateDialpadSize()
//                    updateDialpadBottomMargin()
//                    showDialpad()
//                } else {
//                    binding.dialpadRoundWrapper.root.beGone()
//                    binding.dialpadRectWrapper.root.beGone()
//                    config.dialpadStyle = it
//                    binding.settingsDialpadStyle.text = getDialpadStyleText()
//                    initStyle()
//                    updateDialpadSize()
//                    updateDialpadBottomMargin()
//                    updateCallButtonSize()
//                    showDialpad()
//                }
//
//                val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
//                val buttonSizeVisible = config.dialpadStyle == DIALPAD_GRID || config.dialpadStyle == DIALPAD_ORIGINAL
//                arrayOf(
//                    binding.buttonSizeHolder, binding.buttonSize
//                ).forEach {
//                    it.beVisibleIf(buttonSizeVisible)
//                }
//                arrayOf(
//                binding.buttonSecondSizeHolder, binding.buttonSecondSize
//                ).forEach {
//                it.beVisibleIf(buttonSizeVisible && areMultipleSIMsAvailable)
//            }
//            }
//        }
    }

    private fun getDialpadStyleText() = getString(
        when (config.dialpadStyle) {
            DIALPAD_GRID -> R.string.grid
            DIALPAD_IOS -> R.string.ios_g
            DIALPAD_CONCEPT -> R.string.concept_theme_g
            else -> R.string.clean_theme_g
        }
    )

    private fun setupButtonsColorList() {
        binding.apply {
            initSimCardColor()

            val pro = checkPro()
            val simList = getAvailableSIMCardLabels()
            if (simList.isNotEmpty()) {
                if (simList.size == 1) {
                    val sim1 = simList[0].label
                    settingsSimCardColor1Label.text = if (pro) sim1 else sim1 + " (${getString(R.string.feature_locked)})"
                } else {
                    val sim1 = simList[0].label
                    val sim2 = simList[1].label
                    settingsSimCardColor1Label.text = if (pro) sim1 else sim1 + " (${getString(R.string.feature_locked)})"
                    settingsSimCardColor2Label.text = if (pro) sim2 else sim2 + " (${getString(R.string.feature_locked)})"
                }
            }

            if (pro) {
                settingsSimCardColor1Holder.setOnClickListener {
                    ColorPickerDialog(
                        this@SettingsDialpadActivity,
                        config.simIconsColors[1],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.ic_dialer, theme),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color, wasDefaultPressed ->
                        if (wasPositivePressed || wasDefaultPressed) {
                            if (hasColorChanged(config.simIconsColors[1], color)) {
                                addSimCardColor(1, color)
                                initSimCardColor()
                                initStyle()
                                showDialpad()
                                config.needRestart = true
                            }
                        }
                    }
                }

                settingsSimCardColor2Holder.setOnClickListener {
                    ColorPickerDialog(
                        this@SettingsDialpadActivity,
                        config.simIconsColors[2],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.color_primary, theme),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color, wasDefaultPressed ->
                        if (wasPositivePressed || wasDefaultPressed) {
                            if (hasColorChanged(config.simIconsColors[2], color)) {
                                addSimCardColor(2, color)
                                initSimCardColor()
                                initStyle()
                                showDialpad()
                                config.needRestart = true
                            }
                        }
                    }
                }
            } else {
                arrayOf(
                    settingsSimCardColor1Holder,
                    settingsSimCardColor2Holder
                ).forEach {
                    it.setOnClickListener { view ->
                        shakePurchase()

                        RxAnimation.from(view)
                            .shake(shakeTranslation = 2f)
                            .subscribe()

                        showSnackbar(binding.root)
                    }
                }
            }

            settingsDialpadButtonColorHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(DIALPAD_BUTTONS_STYLE_COLOR, getString(R.string.select_color)),
                    RadioItem(DIALPAD_BUTTONS_STYLE_TRANSPARENT, getString(R.string.transparent_color)),
                    RadioItem(DIALPAD_BUTTONS_STYLE_DEFAULT, getString(R.string.default_color))
                )

                RadioGroupIconDialog(
                    activity = this@SettingsDialpadActivity,
                    items = items,
                    checkedItemId = config.dialpadButtonsColorStyle,
                    titleId = R.string.dialpad_buttons_color
                ) { newValue ->
                    when (newValue) {
                        DIALPAD_BUTTONS_STYLE_COLOR -> {
                            ColorPickerDialog(
                                this@SettingsDialpadActivity,
                                config.dialpadButtonsColor,
                                title = resources.getString(R.string.dialpad_buttons_color)
                            ) { wasPositivePressed, color, wasDefaultPressed ->
                                if (wasPositivePressed) {
//                                    if (hasColorChanged(config.dialpadButtonsColor, color)) {
                                        config.dialpadButtonsColor = color
                                        config.dialpadButtonsColorStyle = DIALPAD_BUTTONS_STYLE_COLOR
                                        updateDialpadButton()
                                        initStyle()
                                        showDialpad()
                                        config.needRestart = true
//                                    }
                                }
                            }
                        }

                        DIALPAD_BUTTONS_STYLE_TRANSPARENT -> {
                            config.dialpadButtonsColorStyle = DIALPAD_BUTTONS_STYLE_TRANSPARENT
                            updateDialpadButton()
                            initStyle()
                            showDialpad()
                            config.needRestart = true

                        }

                        else -> {
                            config.dialpadButtonsColorStyle = DIALPAD_BUTTONS_STYLE_DEFAULT
                            updateDialpadButton()
                            initStyle()
                            showDialpad()
                            config.needRestart = true

                        }
                    }
                }
            }
        }
    }

    private fun initSimCardColor() {
        binding.apply {
            val pro = checkPro()
            arrayOf(
                settingsSimCardColor1Holder,
                settingsSimCardColor2Holder
            ).forEach {
                it.alpha = if (pro) 1f else 0.4f
            }
            val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
            settingsSimCardColor2Holder.beVisibleIf(areMultipleSIMsAvailable)
            if (areMultipleSIMsAvailable) settingsSimCardColor1Icon.setImageResource(R.drawable.ic_phone_one_vector)
            settingsSimCardColor1Icon.background.setTint(config.simIconsColors[1])
            settingsSimCardColor2Icon.background.setTint(config.simIconsColors[2])
            settingsSimCardColor1Icon.setColorFilter(config.simIconsColors[1].getContrastColor())
            settingsSimCardColor2Icon.setColorFilter(config.simIconsColors[2].getContrastColor())
        }
    }

    private fun addSimCardColor(index: Int, color: Int) {
        val recentColors = config.simIconsColors

        recentColors.removeAt(index)
        recentColors.add(index, color)

        val needUpdate = baseConfig.simIconsColors != recentColors
        baseConfig.simIconsColors = recentColors

        if (needUpdate) {
            val recents = config.parseRecentCallsCache()
            val recentsNew = mutableListOf<RecentCall>()
            recents.forEach { recent ->
                val recentNew = if (recent.simID == index) recent.copy(simColor = color) else recent
                recentsNew.add(recentNew)
            }
            config.recentCallsCache = Gson().toJson(recentsNew.take(RECENT_CALL_CACHE_SIZE))
            config.needUpdateRecents = true
            config.needRestart = true
        }
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun setupPrimarySimCard() {
        val simList = getAvailableSIMCardLabels()
        if (simList.size > 1) {
            binding.settingsPrimarySimCardHolder.beVisibleIf(areMultipleSIMsAvailable())
            binding.settingsPrimarySimCard.text = if (config.currentSIMCardIndex == 0) simList[0].label else simList[1].label
            binding.settingsPrimarySimCardHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(0, simList[0].label),
                    RadioItem(1, simList[1].label),
                )

                RadioGroupDialog(this@SettingsDialpadActivity, items, config.currentSIMCardIndex, R.string.primary_sim_card) {
                    config.currentSIMCardIndex = it as Int
                    binding.settingsPrimarySimCard.text = if (config.currentSIMCardIndex == 0) simList[0].label else simList[1].label
                    initStyle()
                    showDialpad()
                }
            }
        } else binding.settingsPrimarySimCardHolder.beGone()
    }

    private fun setupHideDialpadLetters() {
        binding.apply {
            settingsHideDialpadLetters.isChecked = config.hideDialpadLetters
            settingsHideDialpadLettersHolder.setOnClickListener {
                settingsHideDialpadLetters.toggle()
                config.hideDialpadLetters = settingsHideDialpadLetters.isChecked
                binding.settingsDialpadSecondaryLanguageHolder.beGoneIf(config.hideDialpadLetters)
                binding.settingsDialpadSecondaryTypefaceHolder.beGoneIf(config.hideDialpadLetters)
                settingsShowVoicemailIconHolder.beVisibleIf(!config.hideDialpadLetters)
                config.needRestart = true
                initStyle()
                showDialpad()
            }
        }
    }

    private fun setupShowVoicemailIcon() {
        binding.apply {
            settingsShowVoicemailIconHolder.beVisibleIf(!config.hideDialpadLetters)
            settingsShowVoicemailIcon.isChecked = config.showVoicemailIcon
            settingsShowVoicemailIconHolder.setOnClickListener {
                settingsShowVoicemailIcon.toggle()
                config.showVoicemailIcon = settingsShowVoicemailIcon.isChecked
                config.needRestart = true
                initStyle()
                showDialpad()
            }
        }
    }

    private fun getLanguageName(lang: String?): String? {
        return when (lang) {
            LANGUAGE_NONE -> getString(R.string.none)
            LANGUAGE_SYSTEM -> getString(R.string.auto_theme)
            else -> {
                val currentLocale = Locale.getDefault()
                val locale = Locale(lang!!)
                locale.getDisplayLanguage(currentLocale)
            }
        }
    }

    private fun setupDialpadSecondaryLanguage() {
        binding.settingsDialpadSecondaryLanguageHolder.beGoneIf(config.hideDialpadLetters)
        binding.settingsDialpadSecondaryLanguage.text = getLanguageName(config.dialpadSecondaryLanguage)
        binding.settingsDialpadSecondaryLanguageHolder.setOnClickListener {
            val items: ArrayList<RadioItem> = arrayListOf(
                RadioItem(SECONDARY_LANGUAGE_NONE_ID, getString(R.string.none)),
                RadioItem(SECONDARY_LANGUAGE_SYSTEM_ID, getString(R.string.auto_theme))
            )
            val supportedLanguages = DialpadT9.getSupportedSecondaryLanguages()
            for (i in supportedLanguages.indices) {
                items.add(RadioItem(i, getLanguageName(supportedLanguages[i])!!))
            }
            val checkedItemId =
                if (config.dialpadSecondaryLanguage == LANGUAGE_SYSTEM) SECONDARY_LANGUAGE_SYSTEM_ID
                else supportedLanguages.indexOf(config.dialpadSecondaryLanguage)

            RadioGroupDialog(
                this@SettingsDialpadActivity,
                items,
                checkedItemId,
                R.string.secondary_dialpad_language,
                defaultItemId = SECONDARY_LANGUAGE_SYSTEM_ID
            ) {
                val index = it as Int
                if (index == -2) {
                    config.dialpadSecondaryLanguage = LANGUAGE_SYSTEM
                } else if (index == -1 || index >= supportedLanguages.size) {
                    config.dialpadSecondaryLanguage = LANGUAGE_NONE
                } else {
                    config.dialpadSecondaryLanguage = supportedLanguages[it]
                }
                binding.settingsDialpadSecondaryLanguage.text = getLanguageName(config.dialpadSecondaryLanguage)
                initStyle()
                showDialpad()
                config.needRestart = true
            }
        }
    }

    private fun getTypefaceName(typeface: Int): String {
        return when (typeface) {
            Typeface.BOLD -> getString(R.string.typeface_bold)
            Typeface.ITALIC -> getString(R.string.typeface_italic)
            Typeface.BOLD_ITALIC -> getString(R.string.typeface_bold_italic)
            else -> getString(R.string.typeface_normal)
        }
    }

    private fun setupDialpadSecondaryTypeface() {
        binding.settingsDialpadSecondaryTypefaceHolder.beGoneIf(config.hideDialpadLetters)
        binding.settingsDialpadSecondaryTypeface.text = getTypefaceName(config.dialpadSecondaryTypeface)
        binding.settingsDialpadSecondaryTypefaceHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(Typeface.NORMAL, getString(R.string.typeface_normal)),
                RadioItem(Typeface.BOLD, getString(R.string.typeface_bold), icon = R.drawable.ic_bold),
                RadioItem(Typeface.ITALIC, getString(R.string.typeface_italic), icon = R.drawable.ic_italic),
                RadioItem(Typeface.BOLD_ITALIC, getString(R.string.typeface_bold_italic), icon = R.drawable.ic_bold_italic),
            )

            RadioGroupIconDialog(
                this@SettingsDialpadActivity,
                items,
                config.dialpadSecondaryTypeface,
                R.string.typeface,
                defaultItemId = Typeface.NORMAL
            ) {
                config.dialpadSecondaryTypeface = it as Int
                binding.settingsDialpadSecondaryTypeface.text = getTypefaceName(config.dialpadSecondaryTypeface)
                config.needRestart = true
                initStyle()
                showDialpad()
            }
        }
    }

    private fun getHashtagLongClickName(hashtagLongClick: Int): String {
        return when (hashtagLongClick) {
            DIALPAD_LONG_CLICK_SETTINGS -> getString(R.string.settings)
            DIALPAD_LONG_CLICK_SETTINGS_DIALPAD -> getString(R.string.dialpad_preferences)
            else -> "; (wait)"
        }
    }

    private fun setupDialpadHashtagLongClick() {
        binding.settingsDialpadHashtagLongClickLabel.text = getString(R.string.long_click_g, " #")
        binding.settingsDialpadHashtagLongClick.text = getHashtagLongClickName(config.dialpadHashtagLongClick)
        binding.settingsDialpadHashtagLongClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(DIALPAD_LONG_CLICK_WAIT, getString(R.string.add_wait)),
                RadioItem(DIALPAD_LONG_CLICK_SETTINGS, getString(R.string.settings)),
                RadioItem(DIALPAD_LONG_CLICK_SETTINGS_DIALPAD, getString(R.string.dialpad_preferences)),
            )

            RadioGroupDialog(
                this@SettingsDialpadActivity,
                items,
                config.dialpadHashtagLongClick,
                defaultItemId = DIALPAD_LONG_CLICK_SETTINGS_DIALPAD
            ) {
                config.dialpadHashtagLongClick = it as Int
                binding.settingsDialpadHashtagLongClick.text = getHashtagLongClickName(config.dialpadHashtagLongClick)
            }
        }
    }

    private fun setupClearDialpad() {
        binding.apply {
            settingsClearDialpad.isChecked = config.dialpadClearWhenStartCall
            settingsClearDialpadHolder.setOnClickListener {
                settingsClearDialpad.toggle()
                config.dialpadClearWhenStartCall = settingsClearDialpad.isChecked
            }
        }
    }

    private fun setupDialpadVibrations() {
        binding.apply {
            settingsDialpadVibration.isChecked = config.dialpadVibration
            settingsDialpadVibrationHolder.setOnClickListener {
                settingsDialpadVibration.toggle()
                config.dialpadVibration = settingsDialpadVibration.isChecked
            }
        }
    }

    private fun setupDialpadBeeps() {
        updateWrapperToneVolume()
        binding.apply {
            settingsDialpadBeeps.isChecked = config.dialpadBeeps
            settingsDialpadBeepsHolder.setOnClickListener {
                settingsDialpadBeeps.toggle()
                config.dialpadBeeps = settingsDialpadBeeps.isChecked
                toneVolumeWrapper.beVisibleIf(config.dialpadBeeps)
                updateWrapperToneVolume()
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        try {
            super.onRestoreInstanceState(savedInstanceState)
        } catch (_: ClassCastException) {
            // Ignored error restoring ProgressBar state when changing system theme
            Log.w("ProgressBarFix", "Ignoring ProgressBar state restoration error")
        }
    }

    private fun updateWrapperToneVolume() {
        val wrapperColor = if (config.dialpadBeeps) getColoredMaterialStatusBarColor() else getSurfaceColor()
        binding.settingsDialpadBeepsWrapper.background.applyColorFilter(wrapperColor)
    }

    private fun setupToneVolume() {
        binding.apply {
            toneVolumeWrapper.beVisibleIf(config.dialpadBeeps)

            val progress = config.toneVolume
            toneVolumeSeekBar.progress = progress
            val textProgress = "$progress %"
            toneVolumeValue.text = textProgress

            toneVolumeSeekBar.min = 1

            toneVolumeMinus.setOnClickListener {
                toneVolumeSeekBar.progress -= 1
            }
            toneVolumeValue.setOnClickListener {
                toneVolumeSeekBar.progress = 80
            }
            toneVolumePlus.setOnClickListener {
                toneVolumeSeekBar.progress += 1
            }

            toneVolumeSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val textPercent = "$progress %"
                    binding.toneVolumeValue.text = textPercent
                    config.toneVolume = progress
                }
            })
        }
    }

    private fun setupPurchaseThankYou() {
        updatePro()
        binding.dialpadPurchaseThankYouHolder.onClick = { launchPurchase() }
    }

    private fun updatePro(isPro: Boolean = checkPro()) {
        binding.apply {
            dialpadPurchaseThankYouHolder.beGoneIf(isPro)

            arrayOf(
                settingsSimCardColor1Holder,
                settingsSimCardColor2Holder
            ).forEach {
                it.alpha = if (isPro) 1f else 0.4f
            }
        }
    }

    private fun shakePurchase() {
        RxAnimation.from(binding.dialpadPurchaseThankYouHolder)
            .shake()
            .subscribe()
    }

    private fun checkPro() = isOrWasThankYouInstalled(false) || isPro() || isCollection()
}
