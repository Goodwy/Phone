package dev.goodwy.phone.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.telecom.Call
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.behaviorule.arturdumchev.library.pixels
import com.behaviorule.arturdumchev.library.setHeight
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.google.android.material.snackbar.Snackbar
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import dev.goodwy.phone.BuildConfig
import dev.goodwy.phone.R
import dev.goodwy.phone.adapters.ViewPagerAdapter
import dev.goodwy.phone.databinding.ActivityMainBinding
import dev.goodwy.phone.dialogs.ChangeSortingDialog
import dev.goodwy.phone.dialogs.FilterContactSourcesDialog
import dev.goodwy.phone.extensions.*
import dev.goodwy.phone.fragments.ContactsFragment
import dev.goodwy.phone.fragments.FavoritesFragment
import dev.goodwy.phone.fragments.MyViewPagerFragment
import dev.goodwy.phone.fragments.RecentsFragment
import dev.goodwy.phone.helpers.*
import dev.goodwy.phone.models.AudioRoute
import dev.goodwy.phone.models.Events
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.alpha
import com.mikhaellopez.rxanimation.fadeIn
import com.mikhaellopez.rxanimation.fadeOut
import com.mikhaellopez.rxanimation.scale
import com.mikhaellopez.rxanimation.shake
import dev.goodwy.phone.models.SpeedDial
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import me.grantland.widget.AutofitHelper
import java.io.InputStreamReader
import java.util.Locale
import java.util.Objects
import kotlin.math.roundToInt

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedStartNameWithSurname = false
    private var storedShowPhoneNumbers = false
    private var storedBackgroundColor = 0
    private var storedToneVolume = 0
    private var currentOldScrollY = 0
    var cachedContacts = ArrayList<Contact>()
    private var cachedFavorites = ArrayList<Contact>()
    private var storedContactShortcuts = ArrayList<Contact>()
    private var isSpeechToTextAvailable = false

    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private var speedDialValues = ArrayList<SpeedDial>()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    var isDialpadVisible = false
    private var isDialpadVisibleStored = false
    private var menuHeight = 0
    private var dialpadHeight = 0
    private var isTalkBackOn = false

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        val showTabs = config.showTabs
        val oneTabs: Boolean = showTabs and TAB_FAVORITES == 0 && showTabs and TAB_CONTACTS == 0
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.mainTabsHolder),
            moveBottomSystem = if (oneTabs) listOf(binding.mainDialpadButton) else emptyList()
        )

        setupOptionsMenu()
        refreshMenuItems()
        storeStateVariables()

        EventBus.getDefault().register(this)
        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false
        val properBackgroundColor = getProperBackgroundColor()

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(
                    binding.mainHolder,
                    R.string.allow_displaying_over_other_apps,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(properBackgroundColor.darkenColor())
                val properTextColor = getProperTextColor()
                snackbar.setTextColor(properTextColor)
                snackbar.setActionTextColor(properTextColor)
                val snackBarView: View = snackbar.view
                snackBarView.translationY = -pixels(R.dimen.snackbar_bottom_margin)
                snackbar.show()
            }

            handleFullScreenNotificationsPermission { granted ->
                if (!granted) {
                    toast(com.goodwy.commons.R.string.notifications_disabled)
                } else {
                    checkWhatsNewDialog()
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (baseConfig.blockUnknownNumbers || baseConfig.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        binding.mainMenu.apply {
            searchBeVisibleIf(config.showSearchBar)
            updateTitle(getAppLauncherName())
        }

        setupTabs()
        Contact.sorting = config.sorting

        setupSecondaryLanguage()

        // At the first launch, enable the general blocking if at least one blocking was enabled
        if (config.initCallBlockingSetup) {
            if (getBlockedNumbers().isNotEmpty() || baseConfig.blockUnknownNumbers || baseConfig.blockHiddenNumbers) {
                baseConfig.blockingEnabled = true
            }
            config.initCallBlockingSetup = false
        }

        CallManager.addListener(callCallback)
        binding.mainCallButton.setOnClickListener { startActivity(Intent(this, CallActivity::class.java)) }

        initDialpad()
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs || storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        @SuppressLint("UnsafeIntentLaunch")
        if (config.needRestart || storedBackgroundColor != getProperBackgroundColor()) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        if (storedToneVolume != config.toneVolume) {
            toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)
        }
        isTalkBackOn = isTalkBackOn()
        speedDialValues = config.getSpeedDialValues()

        val size = config.dialpadSize
        val dimens = pixels(R.dimen.dialpad_height)
        dialpadHeight = (dimens * (size / 100f)).toInt()
        updateDialpadSize()

        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(this, R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        setupTabColors()
        binding.mainMenu.updateColors(
            background = getStartRequiredStatusBarColor(),
            scrollOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        )

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        if (binding.viewPager.adapter != null) {
            getAllFragments().forEach {
                it?.setupColors(properTextColor, properPrimaryColor, getProperAccentColor())
            }
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)
        binding.viewPager.setPagingEnabled(!config.useSwipeToAction)

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        if (useSurfaceColor) binding.mainHolder.setBackgroundColor(getSurfaceColor())
        getAllFragments().forEach {
            it?.setBackgroundColor(backgroundColor)
        }
        if (getCurrentFragment() is RecentsFragment) clearMissedCalls()

        updateState()
        checkShortcuts()
        checkErrorDialog()
        newAppRecommendation()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowTabs = showTabs
            storedStartNameWithSurname = startNameWithSurname
            storedShowPhoneNumbers = showPhoneNumbers
            storedFontSize = fontSize
            needRestart = false
        }
        storedBackgroundColor = getProperBackgroundColor()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        } else if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.mainMenu.setText(speechToText)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressedCompat(): Boolean {
        return when (CallManager.getPhoneState()) {
            NoCall -> {
                when {
                    binding.mainMenu.isSearchOpen -> {
                        binding.mainMenu.closeSearch()
                        true
                    }

                    isDialpadVisible -> {
                        hideDialpad()
                        isDialpadVisibleStored = false
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(callCallback)
        EventBus.getDefault().unregister(this)
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val getRecentsFragment = getRecentsFragment()
        val getFavoritesFragment = getFavoritesFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment
            findItem(R.id.filter).isVisible = currentFragment != getRecentsFragment
            findItem(R.id.create_new_contact).isVisible = currentFragment == getContactsFragment()
            findItem(R.id.change_view_type).isVisible = currentFragment == getFavoritesFragment
            findItem(R.id.column_count).isVisible = currentFragment == getFavoritesFragment && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.show_blocked_numbers).isVisible = currentFragment == getRecentsFragment
            findItem(R.id.show_blocked_numbers).title =
                if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
//            toggleHideOnScroll(false)
            if (baseConfig.useSpeechToText) {
                isSpeechToTextAvailable = isSpeechToTextAvailable()
                showSpeechToText = isSpeechToTextAvailable
            }

            setupMenu()

            onSpeechToTextClickListener = {
                speechToText()
            }

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
                clearSearch()
            }

            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.show_blocked_numbers -> showBlockedNumbers()
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.settings -> launchSettings()
                    R.id.about -> launchAbout()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount, R.string.column_count) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        config.viewType = if (config.viewType == VIEW_TYPE_LIST) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        refreshMenuItems()
        getFavoritesFragment()?.refreshItems()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun showBlockedNumbers() {
        config.showBlockedNumbers = !config.showBlockedNumbers
        binding.mainMenu.requireToolbar().menu.findItem(R.id.show_blocked_numbers).title =
            if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)
        config.needUpdateRecents = true
        runOnUiThread {
            getRecentsFragment()?.refreshItems()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(invalidate = true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val iconColor = getProperPrimaryColor()
        if (config.lastHandledShortcutColor != iconColor) {
            val launchDialpad = getLaunchDialpadShortcut(iconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = iconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(iconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, MainActivity::class.java)
        intent.data = Uri.fromParts("tel", "", null)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun createContactShortcuts() {
        ensureBackgroundThread {
            if (isRPlus() && shortcutManager.isRequestPinShortcutSupported) {
                val starred = cachedFavorites.filter { it.phoneNumbers.isNotEmpty() }.take(3)
                if (storedContactShortcuts != starred) {
                    val allShortcuts = shortcutManager.dynamicShortcuts.filter { it.id != "launch_dialpad" }.map { it.id }
                    shortcutManager.removeDynamicShortcuts(allShortcuts)

                    storedContactShortcuts.clear()
                    storedContactShortcuts.addAll(starred)

                    starred.reversed().forEach { contact ->
                        val name = contact.getNameToDisplay()
                        getShortcutImageNeedBackground(contact.photoUri, name) { image ->
                            this.runOnUiThread {
                                val number = if (contact.phoneNumbers.size == 1) {
                                    contact.phoneNumbers[0].normalizedNumber
                                } else {
                                    contact.phoneNumbers.firstOrNull { it.isPrimary }?.normalizedNumber
                                }

                                if (number != null) {
                                    this.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                                        val intent = Intent(action).apply {
                                            data = Uri.fromParts("tel", number, null)
                                            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
                                        }

                                        val shortcut = ShortcutInfo.Builder(this, "contact_${contact.id}")
                                            .setShortLabel(name)
                                            .setIcon(Icon.createWithAdaptiveBitmap(image))
                                            .setIntent(intent)
                                            .build()
                                        this.shortcutManager.pushDynamicShortcut(shortcut)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
        }

        val bottomBarColor =
            if (isDynamicTheme() && !isSystemInDarkMode()) getColoredMaterialStatusBarColor()
            else getSurfaceColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector_scaled)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_scaled)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded_scaled)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded)
        }

        return icons
    }

    @Suppress("DEPRECATION")
    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (config.changeColourTopBar) scrollChange()
            }

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()

                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
                if (getCurrentFragment() == getRecentsFragment()) {
                    if (isDialpadVisibleStored) showDialpad()
                    clearMissedCalls()
                } else {
                    hideDialpad()
                }
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = binding.mainTabsHolder.tabCount - 2
                }

                val checkDialIntent = checkDialIntent()
                // If you need to open the dial pad, switch to the call history tab.
                if ((config.openDialPadAtLaunch && !launchedDialer) || checkDialIntent) {
                    // Find the call history tab index
                    val showTabs = config.showTabs
                    var callHistoryIndex = 0

                    if (showTabs and TAB_FAVORITES != 0) {
                        callHistoryIndex++
                    }

                    if (showTabs and TAB_CALL_HISTORY != 0) {
                        wantedTab = callHistoryIndex
                    }
                }

                binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                refreshMenuItems()

                // Displaying the dial pad after switching tabs
                if ((config.openDialPadAtLaunch && !launchedDialer) || checkDialIntent) {
                    Handler().postDelayed({
                        showDialpad()
                        launchedDialer = true
                        isDialpadVisibleStored = true
                    }, 100L) // A slight delay for stability
                }
            }, 100L)
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
            if (config.changeColourTopBar) scrollChange()
        }

//        if (config.openDialPadAtLaunch && !launchedDialer) {
//            showDialpad()
//            launchedDialer = true
//            isDialpadVisibleStored = true
//        }
    }

    private fun scrollChange() {
        val myRecyclerView = getCurrentFragment()?.myRecyclerView()
        scrollingView = myRecyclerView

        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        currentOldScrollY = scrollingViewOffset

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        val statusBarColor = if (config.changeColourTopBar) getRequiredStatusBarColor(useSurfaceColor) else backgroundColor

        binding.mainMenu.updateColors(statusBarColor, scrollingViewOffset)
        setupSearchMenuScrollListener(
            scrollingView = myRecyclerView,
            searchMenu = binding.mainMenu,
            surfaceColor = useSurfaceColor
        )
    }

    private fun setupTabs() {
        // bottom tab bar
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        beGoneIf(config.useIconTabs)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                if (config.closeSearch) {
                    binding.mainMenu.closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (binding.mainMenu.isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }

                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

//                val lastPosition = binding.mainTabsHolder.tabCount - 1
//                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
//                    clearMissedCalls()
//                }

                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        binding.mainMenu.requestFocusAndShowKeyboard()
                    }
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        storedShowPhoneNumbers = config.showPhoneNumbers
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.recents
            else -> R.string.contacts_tab
        }

        return resources.getString(stringId)
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_star_vector
            1 -> R.drawable.ic_clock_filled_vector
            else -> R.drawable.ic_person_rounded
        }
        return resources.getColoredDrawableWithColor(this@MainActivity, drawableId, getProperTextColor())!!
    }

    private fun getTabContentDescription(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.call_history_tab
            else -> R.string.contacts_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    fun refreshFragments() {
        cacheContacts()
        getContactsFragment()?.refreshItems()
        getFavoritesFragment()?.refreshItems()
        getRecentsFragment()?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        val mainTabsHolder = binding.mainTabsHolder
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_FAVORITES -> 0
            TAB_CALL_HISTORY -> if (showTabsMask and TAB_FAVORITES > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CONTACTS > 0) {
                    if (showTabsMask and TAB_FAVORITES > 0) {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun launchSettings() {
        binding.mainMenu.closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            config.needUpdateRecents = true
            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts() {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(this).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in config.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (_: Exception) {
            }
        }
    }

    fun cacheFavorites(contacts: List<Contact>) {
        try {
            cachedFavorites.clear()
            cachedFavorites.addAll(contacts)
        } catch (_: Exception) {
        }
        createContactShortcuts()
    }

    private fun setupSecondaryLanguage() {
        if (!DialpadT9.Initialized) {
            val reader = InputStreamReader(resources.openRawResource(R.raw.t9languages))
            DialpadT9.readFromJson(reader.readText())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(event: Events.RefreshCallLog) {
        config.needUpdateRecents = true
        getRecentsFragment()?.refreshItems(needUpdate = true)
    }

    private fun checkWhatsNewDialog() {
        whatsNewList().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    private val callCallback = object : CallManagerListener {
        override fun onStateChanged() {
            updateState()
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {
        }

        override fun onPrimaryCallChanged(call: Call) {
            updateState()
        }
    }

    private fun updateState() {
        val phoneState = CallManager.getPhoneState()
        when (phoneState) {
            is SingleCall -> {
                RxAnimation.together(
                    binding.mainCallButton.scale(1.1f),
                    binding.mainCallButton.fadeIn(duration = 260),
                ).andThen(
                    binding.mainCallButton.scale(1f)
                ).subscribe()
//                val state = phoneState.call.getStateCompat()
//                if (state == Call.STATE_RINGING) {
//                }
            }

            is TwoCalls -> { }

            else -> {
                RxAnimation.together(
                    binding.mainCallButton.scale(0.6f),
                    binding.mainCallButton.fadeOut(duration = 360),
                ).subscribe()
            }
        }
    }

    private fun checkErrorDialog() {
        if (baseConfig.lastError != "") {
            ConfirmationDialog(
                this,
                "An error occurred while the application was running. Please send us this error so we can fix it.",
                positive = R.string.send_email
            ) {
                val body = "Dialer : LastError"
                val address = getMyMailString()
                val lastError = baseConfig.lastError

                val emailIntent = Intent(ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                    putExtra(Intent.EXTRA_SUBJECT, body)
                    putExtra(Intent.EXTRA_TEXT, lastError)

                    // set the type for better compatibility
                    type = "message/rfc822"
                }

                try {
                    startActivity(Intent.createChooser(emailIntent, "Send email"))
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                } catch (e: Exception) {
                    showErrorToast(e)
                }

                baseConfig.lastError = ""
            }
        }
    }

    //Dialpad
    private fun checkDialIntent(): Boolean {
        return if (
            (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
            && intent.data != null && intent.dataString?.contains("tel:") == true
        ) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            binding.dialpadWrapper.dialpadInput.setText(number)
            binding.dialpadWrapper.dialpadInput.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun initDialpad() {
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

        updateDialpadButton()

        speedDialValues = config.getSpeedDialValues()
        storedToneVolume = config.toneVolume
        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.dialpadInput.apply {
            if (config.formatPhoneNumbers) {
                @Suppress("DEPRECATION")
                addTextChangedListener(PhoneNumberFormattingTextWatcher(Locale.getDefault().country))
            }
            onTextChangeListener { dialpadValueChanged(it) }
//            requestFocus()
            AutofitHelper.create(this@apply)
            disableKeyboard()
        }

        setupDialpadAnimation()

        binding.mainDialpadButton.setOnClickListener {
            if (isDialpadVisible) {
                hideDialpad()
                isDialpadVisibleStored = false
            } else {
                binding.mainTabsHolder.apply {
                    val index = if (config.showTabs and TAB_FAVORITES > 0) 1 else 0
                    getTabAt(index)?.select()
                }
                showDialpad()
                isDialpadVisibleStored = true
            }
        }

        //style
        if (!DialpadT9.Initialized) {
            val reader = InputStreamReader(resources.openRawResource(R.raw.t9languages))
            DialpadT9.readFromJson(reader.readText())
        }

        val surfaceColor =
            if (isDynamicTheme() && !isSystemInDarkMode()) getProperBackgroundColor() else getSurfaceColor()

        binding.dialpadWrapper.dialpadBottomMargin.setBackgroundColor(surfaceColor)
        binding.dialpadWrapper.dialpadInputHolder.setBackgroundColor(surfaceColor)
        binding.dialpadWrapper.apply {
            dialpadVoicemail.beVisibleIf(config.showVoicemailIcon && !config.hideDialpadLetters)
            dialpadHolder.setBackgroundColor(surfaceColor)

            if (isPiePlus()) {
                val textColor = getProperTextColor()
                dialpadHolder.outlineAmbientShadowColor = textColor
                dialpadHolder.outlineSpotShadowColor = textColor
            }

//            dialpadBottomMargin.apply {
//                setBackgroundColor(surfaceColor)
//                setHeight(100)
//            }
        }
        initLetters()
    }

    @SuppressLint("SetTextI18n")
    private fun initLetters() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val getProperTextColor = getProperTextColor()

        binding.dialpadWrapper.apply {
            if (config.hideDialpadLetters) {
                arrayOf(
                    dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
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
                    setOnClickListener {
                        if (config.currentSIMCardIndex == 0) config.currentSIMCardIndex = 1 else config.currentSIMCardIndex = 0
                        updateCallButton()
                        maybePerformDialpadHapticFeedback(dialpadHideHolder)
                        RxAnimation.from(dialpadCallButton)
                            .shake()
                            .subscribe()
                    }
                }
                dialpadHideHolder.setOnLongClickListener {
                    if (config.currentSIMCardIndex == 0) config.currentSIMCardIndex = 1 else config.currentSIMCardIndex = 0
                    updateCallButton()
                    maybePerformDialpadHapticFeedback(dialpadHideHolder)
                    RxAnimation.from(dialpadCallButton)
                        .shake()
                        .subscribe()
                    true
                }
                updateCallButton()
                dialpadCallButton.setOnClickListener {
                    initCall(binding.dialpadWrapper.dialpadInput.value, config.currentSIMCardIndex)
                    maybePerformDialpadHapticFeedback(dialpadCallButton)
                }
                dialpadCallButton.contentDescription = getString(
                    if (config.currentSIMCardIndex == 0) R.string.call_from_sim_1 else R.string.call_from_sim_2
                )
            } else {
                dialpadHide.applyColorFilter(getProperTextColor)
                dialpadChangeSim.beGone()
                val color = config.simIconsColors[1]
                val callIcon = resources.getColoredDrawableWithColor(
                    this@MainActivity,
                    R.drawable.ic_phone_vector,
                    color.getContrastColor()
                )
                dialpadCallIcon.setImageDrawable(callIcon)
                dialpadCallButton.background.applyColorFilter(color)
                dialpadCallButton.setOnClickListener {
                    initCall(binding.dialpadWrapper.dialpadInput.value, 0)
                    maybePerformDialpadHapticFeedback(dialpadCallButton)
                }
                dialpadCallButton.contentDescription = getString(R.string.call)
            }

            dialpadHideHolder.setOnClickListener {
                hideDialpad()
                isDialpadVisibleStored = false
                maybePerformDialpadHapticFeedback(dialpadHideHolder)
            }

            dialpadCallButton.setOnLongClickListener {
                if (binding.dialpadWrapper.dialpadInput.value.isEmpty()) {
                    val text = getTextFromClipboard()
                    binding.dialpadWrapper.dialpadInput.setText(text)
                    if (text != null && text != "") {
                        binding.dialpadWrapper.dialpadInput.setSelection(text.length)
                        binding.dialpadWrapper.dialpadInput.requestFocusFromTouch()
                    }; true
                } else {
                    copyNumber(); true
                }
            }

//            dialpadClearCharHolder.beVisibleIf(binding.dialpadWrapper.dialpadInput.value.isNotEmpty() || areMultipleSIMsAvailable)
            dialpadClearChar.applyColorFilter(Color.GRAY)
            dialpadClearChar.alpha = 0.4f
            dialpadClearCharX.applyColorFilter(getProperTextColor)
            dialpadClearCharHolder.setOnClickListener { clearChar(it) }
            dialpadClearCharHolder.setOnLongClickListener { clearInput(); true }

            //dialpadHolder.setOnClickListener { binding.dialpadWrapper.dialpadInput.setText(getTextFromClipboard()); true }
            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            //setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*')
            setupCharClick(dialpadHashtagHolder, '#')
            dialpadHolder.setOnClickListener { } //Do not press between the buttons

            dialpadMenu.apply {
                applyColorFilter(getProperTextColor)
                setOnClickListener {
                    val wrapper: Context = ContextThemeWrapper(this@MainActivity, getPopupMenuTheme())
                    val popupMenu = PopupMenu(wrapper, dialpadMenu, Gravity.END)
                    val dialpadInputNotEmpty = binding.dialpadWrapper.dialpadInput.value.isNotEmpty()
                    popupMenu.menu.add(1, 1, 1, R.string.paste_g).setIcon(R.drawable.ic_paste)
                    if (dialpadInputNotEmpty) popupMenu.menu.add(1, 2, 2, R.string.copy).setIcon(R.drawable.ic_copy_vector)
                    if (dialpadInputNotEmpty) popupMenu.menu.add(1, 3, 3, R.string.search_the_web).setIcon(R.drawable.ic_search_vector)
                    if (dialpadInputNotEmpty) popupMenu.menu.add(1, 4, 4, R.string.call_anonymously).setIcon(R.drawable.ic_call_made_vector)
                    if (dialpadInputNotEmpty) popupMenu.menu.add(1, 5, 5, R.string.send_sms).setIcon(R.drawable.ic_messages)
                    if (dialpadInputNotEmpty) popupMenu.menu.add(1, 6, 6, R.string.add_number_to_contact).setIcon(R.drawable.ic_add_person_vector)
                    popupMenu.menu.add(1, 7, 7, R.string.add_pause).setIcon(R.drawable.ic_transparent)
                    popupMenu.menu.add(1, 8, 8, R.string.add_wait).setIcon(R.drawable.ic_transparent)

                    popupMenu.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                val text = getTextFromClipboard()
                                binding.dialpadWrapper.dialpadInput.setText(text)
                                if (text != null && text != "") {
                                    binding.dialpadWrapper.dialpadInput.setSelection(text.length)
                                    binding.dialpadWrapper.dialpadInput.requestFocusFromTouch()
                                }
                            }

                            2 -> copyNumber()

                            3 -> webSearch()

                            4 -> initCallAnonymous()

                            5 -> sendSMS()

                            6 -> addNumberToContact()

                            7 -> dialpadPressed(',', dialpadAsteriskHolder)

                            8 -> dialpadPressed(';', dialpadHashtagHolder)

                            else -> { }
                        }
                        true
                    }
                    if (isQPlus()) {
                        popupMenu.setForceShowIcon(true)
                    }
                    popupMenu.show()
                    // icon coloring
                    popupMenu.menu.apply {
                        for (index in 0 until this.size) {
                            val item = this[index]

                            if (isQPlus()) {
                                item.icon!!.colorFilter = BlendModeColorFilter(
                                    getProperTextColor(), BlendMode.SRC_IN
                                )
                            } else {
                                item.icon!!.setColorFilter(getProperTextColor(), PorterDuff.Mode.SRC_IN)
                            }
                        }
                    }

                    //sendSMS(callContact!!.number, "textMessage")
                }
            }
        }
//        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }
    }

    private fun updateCallButton() {
        val oneSim = config.currentSIMCardIndex == 0
        val simColor = if (oneSim) config.simIconsColors[1] else config.simIconsColors[2]
        val callIconId = if (oneSim) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
        val callIcon = resources.getColoredDrawableWithColor(this@MainActivity, callIconId, simColor.getContrastColor())
        binding.dialpadWrapper.dialpadCallIcon.setImageDrawable(callIcon)
        binding.dialpadWrapper.dialpadCallButton.background.applyColorFilter(simColor)
    }

    private fun updateDialpadButton() {
        val buttonColor = getDialpadButtonsColor()
        val textColor = buttonColor.getContrastColor()

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

    private fun initCall(number: String = binding.dialpadWrapper.dialpadInput.value, handleIndex: Int, displayName: String? = null) {
        if (number.isNotEmpty()) {
            val nameToDisplay = displayName ?: number
            if (handleIndex != -1 && areMultipleSIMsAvailable()) {
                callContactWithSimWithConfirmationCheck(number, nameToDisplay, handleIndex == 0)
            } else {
                startCallWithConfirmationCheck(number, nameToDisplay)
            }
            if (config.dialpadClearWhenStartCall) clearInputWithDelay()
        } else {
            RecentsHelper(this).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.dialpadWrapper.dialpadInput.setText(mostRecentNumber)
                        binding.dialpadWrapper.dialpadInput.setSelection(mostRecentNumber.length)
                    }
                }
            }
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadWrapper.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, -1, speedDial.getName(this))
                return true
            } else {
                ConfirmationDialog(this, getString(R.string.open_speed_dial_manage)) {
                    startActivity(Intent(applicationContext, ManageSpeedDialActivity::class.java))
                }
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else if (char == '*') {
            clearChar(view)
            dialpadPressed(',', view)
        } else if (char == '#') {
            clearChar(view)
            when (config.dialpadHashtagLongClick) {
                DIALPAD_LONG_CLICK_WAIT -> dialpadPressed(';', view)
                DIALPAD_LONG_CLICK_SETTINGS_DIALPAD -> {
                    startActivity(Intent(applicationContext, SettingsDialpadActivity::class.java))
                }

                else -> {
                    startActivity(Intent(applicationContext, SettingsActivity::class.java))
                }
            }
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadWrapper.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.dialpadWrapper.dialpadInput.dispatchKeyEvent(binding.dialpadWrapper.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadWrapper.dialpadInput.setText("")
    }

    private fun clearInputWithDelay() {
        lifecycleScope.launch {
            delay(1000)
            clearInput()
        }
    }

    private fun copyNumber() {
        val clip = binding.dialpadWrapper.dialpadInput.value
        copyToClipboard(clip)
    }

    private fun webSearch() {
        val text = binding.dialpadWrapper.dialpadInput.value
        launchInternetSearch(text)
    }

    private fun addNumberToContact() {
        startAddContactIntent(binding.dialpadWrapper.dialpadInput.value)
    }

    private fun initCallAnonymous() {
        val dialpadValue = binding.dialpadWrapper.dialpadInput.value
        if (config.showWarningAnonymousCall) {
            val text = String.format(getString(R.string.call_anonymously_warning), dialpadValue)
            ConfirmationAdvancedDialog(
                this,
                text,
                R.string.call_anonymously_warning,
                R.string.ok,
                R.string.do_not_show_again,
                fromHtml = true
            ) {
                if (it) {
                    initCall("#31#$dialpadValue", 0)
                } else {
                    config.showWarningAnonymousCall = false
                    initCall("#31#$dialpadValue", 0)
                }
            }
        } else {
            initCall("#31#$dialpadValue", 0)
        }
    }

    private fun sendSMS() {
        val dialpadValue = binding.dialpadWrapper.dialpadInput.value
        launchSendSMSIntentRecommendation(dialpadValue)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        if (isTalkBackOn) {
            view.setOnClickListener {
                startDialpadTone(char)
                dialpadPressed(char, view)
                stopDialpadTone(char)
            }
            view.setOnLongClickListener { performLongClick(view, char); true}
        }
        else view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }

    private fun setupDialpadAnimation() {
        binding.mainHolder.post {
            // Obtaining the heights of elements
            menuHeight = binding.mainMenu.measuredHeight

            binding.dialpadWrapper.root.measure(
                View.MeasureSpec.makeMeasureSpec(binding.mainHolder.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            // Initial states
            binding.dialpadWrapper.root.visibility = View.GONE
        }
    }

    private fun showDialpad() {
        if (isDialpadVisible) return
        isDialpadVisible = true
        binding.mainMenu.closeSearch()

        val statusBarHeight = statusBarHeight

        // Instantly set paddingBottom
        binding.viewPager.setPadding(
            binding.viewPager.paddingLeft,
            binding.viewPager.paddingTop,
            binding.viewPager.paddingRight,
            dialpadHeight - statusBarHeight
        )

        // Calculate the offset: menu height minus status bar height
        // so that the menu disappears but the status bar remains visible
        val shiftAmount = if (menuHeight > statusBarHeight) {
            menuHeight - statusBarHeight
        } else {
            menuHeight
        }.toFloat()

        // Creating a set of animations
        val animatorSet = AnimatorSet()
        val interpolator = DecelerateInterpolator()

        // 1. mainMenu and viewPager up
        val menuAnimator = ObjectAnimator.ofFloat(binding.mainMenu, "translationY", -menuHeight.toFloat())
        val pagerAnimator = ObjectAnimator.ofFloat(binding.viewPager, "translationY", -shiftAmount)

        // 2. mainDialpadButton right
        val dialpadButtonAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.mainDialpadButton, "translationX",
                    binding.mainDialpadButton.width.toFloat() * 1.5f),
                ObjectAnimator.ofFloat(binding.mainDialpadButton, "alpha", 0f)
            )
        }

        // 3. Show dialpad
        binding.dialpadWrapper.root.visibility = View.VISIBLE
        binding.dialpadWrapper.root.translationY = dialpadHeight.toFloat()
        val dialpadAnimator = ObjectAnimator.ofFloat(binding.dialpadWrapper.root, "translationY", 0f)

        // 4. Display of the call button
        val callButtonAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.mainCallButton, "translationY",
                    -(dialpadHeight).toFloat())
            )
        }

        // We launch all animations simultaneously
        animatorSet.playTogether(
            menuAnimator,
            pagerAnimator,
            dialpadButtonAnimator,
            dialpadAnimator,
            callButtonAnimator
        )

        animatorSet.duration = 300
        animatorSet.interpolator = interpolator
        animatorSet.start()

        val scale = config.dialpadScale
        RxAnimation.together(
            binding.dialpadWrapper.dialpadWrapper.scale(scale),
            binding.dialpadWrapper.dialpadWrapper.alpha(1f)
        ).subscribe()

        Handler().postDelayed({
            dialpadValueChanged(binding.dialpadWrapper.dialpadInput.value)
        }, 310L)
    }

    private fun hideDialpad() {
        if (!isDialpadVisible) return
        isDialpadVisible = false
        dialpadValueChanged("")

        // Restoring the original padding
        binding.viewPager.setPadding(
            binding.viewPager.paddingLeft,
            binding.viewPager.paddingTop,
            binding.viewPager.paddingRight,
            0
        )

        val animatorSet = AnimatorSet()
        val interpolator = AccelerateInterpolator()

        // 1. Return mainMenu and viewPager
        val menuAnimator = ObjectAnimator.ofFloat(binding.mainMenu, "translationY", 0f)
        val pagerAnimator = ObjectAnimator.ofFloat(binding.viewPager, "translationY", 0f)

        // 2. Return mainDialpadButton
        val dialpadButtonAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.mainDialpadButton, "translationX", 0f),
                ObjectAnimator.ofFloat(binding.mainDialpadButton, "alpha", 1f)
            )
        }

        // 3. Hide the dial pad
        val dialpadAnimator = ObjectAnimator.ofFloat(binding.dialpadWrapper.root, "translationY", dialpadHeight.toFloat())

        // 4. Hide the call button
        val callButtonAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.mainCallButton, "translationY", 0f)
            )
        }

        animatorSet.playTogether(
            menuAnimator,
            pagerAnimator,
            dialpadButtonAnimator,
            dialpadAnimator,
            callButtonAnimator
        )

        animatorSet.duration = 300
        animatorSet.interpolator = interpolator
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Reset states after animation
                binding.dialpadWrapper.root.visibility = View.GONE
                binding.dialpadWrapper.root.translationY = 0f
                binding.viewPager.translationY = 0f
                binding.mainDialpadButton.translationX = 0f
                binding.mainCallButton.translationY = 0f
            }
        })

        animatorSet.start()

        RxAnimation.together(
            binding.dialpadWrapper.dialpadWrapper.scale(0.8F),
            binding.dialpadWrapper.dialpadWrapper.alpha(0f)
        ).subscribe()
    }

    private fun dialpadValueChanged(textFormat: String) {
        val len = textFormat.length

        //Only works for system apps, CALL_PRIVILEGED and MODIFY_PHONE_STATE permissions are required
        if (len > 8 && textFormat.startsWith("*#*#") && textFormat.endsWith("#*#*")) {
            val secretCode = textFormat.substring(4, textFormat.length - 4)
            if (isDefaultDialer()) {
                getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
            } else {
                launchSetDefaultDialerIntent()
            }
            return
        }

        getAllFragments().forEach {
            it?.finishActMode()
        }

        val text = if (config.formatPhoneNumbers) textFormat.removeNumberFormatting() else textFormat
        getCurrentFragment()?.onSearchQueryChanged(text, true)
    }

    private fun updateDialpadSize() {
        val size = config.dialpadSize
        val dimens = pixels(R.dimen.dialpad_height)
        val dialpadHeight = (dimens * (size / 100f)).toInt()
        binding.dialpadWrapper.dialpadWrapper.setHeight(dialpadHeight)

        val scale = config.dialpadScale
        binding.dialpadWrapper.dialpadWrapper.scaleX = scale
        binding.dialpadWrapper.dialpadWrapper.scaleY = scale

        val defaultPadding = pixels(R.dimen.normal_margin)
        binding.dialpadWrapper.dialpadCallIcon.setPadding((defaultPadding * (size / 100f)).toInt())

        val showTabs = config.showTabs
        val oneTabs: Boolean = showTabs and TAB_FAVORITES == 0 && showTabs and TAB_CONTACTS == 0
        val margin = config.dialpadBottomMargin
        val start = if (oneTabs) navigationBarHeight.toFloat() else pixels(R.dimen.zero)
        binding.dialpadWrapper.dialpadBottomMargin.setHeight((start + margin).toInt())
    }

//    private fun updateCallButtonSize() {
//        val size = config.callButtonPrimarySize
//        val view = binding.dialpadWrapper.dialpadCallButton
//        val margin = (view.width * ((100 - size) / 100f)).toInt()
////        view.setHeightAndWidth((height * (size / 100f)).toInt())
////        view.setPadding((height * 0.1765 * (size / 100f)).toInt())
//
//        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
//        layoutParams?.let { params ->
//            params.setMargins(margin, margin, margin, margin) // left, top, right, bottom
//            view.layoutParams = params
//        }
//
////        if (areMultipleSIMsAvailable()) {
////            val sizeSecondary = config.callButtonSecondarySize
////            val viewSecondary = binding.dialpadClearWrapper.dialpadCallTwoButton
////            val dimensSecondary = pixels(R.dimen.dialpad_button_size_small)
////            viewSecondary.setHeightAndWidth((dimensSecondary * (sizeSecondary / 100f)).toInt())
////            viewSecondary.setPadding((dimens * 0.1765 * (sizeSecondary / 100f)).toInt())
////        }
//    }
}
