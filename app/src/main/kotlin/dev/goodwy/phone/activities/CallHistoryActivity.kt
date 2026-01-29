package dev.goodwy.phone.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.text.SpannableString
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.commons.models.contacts.Event
import com.goodwy.commons.models.contacts.SocialAction
import dev.goodwy.phone.BuildConfig
import dev.goodwy.phone.R
import dev.goodwy.phone.adapters.CallHistoryAdapter
import dev.goodwy.phone.databinding.ActivityCallHistoryBinding
import dev.goodwy.phone.databinding.ItemViewEmailBinding
import dev.goodwy.phone.databinding.ItemViewEventBinding
import dev.goodwy.phone.databinding.ItemViewMessengersActionsBinding
import dev.goodwy.phone.dialogs.ChangeTextDialog
import dev.goodwy.phone.dialogs.ChooseSocialDialog
import dev.goodwy.phone.extensions.*
import dev.goodwy.phone.helpers.*
import dev.goodwy.phone.models.RecentCall
import kotlin.collections.ArrayList
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.views.MyLinearLayoutManager
import dev.goodwy.phone.adapters.MainAdapter

class CallHistoryActivity : SimpleActivity() {
    companion object {
        private const val SECTION_CALL_HISTORY = 0
        private const val SECTION_PHONE_NUMBER = 1
        private const val SECTION_MESSENGERS = 2
        private const val SECTION_EMAILS = 3
        private const val SECTION_EVENTS = 4
        private const val SECTION_SIM = 5
        private const val SECTION_NOTES = 6
        private const val SECTION_BLOCK = 7
    }

    private val binding by viewBinding(ActivityCallHistoryBinding::inflate)

    private var allRecentCall = listOf<RecentCall>()
    private var contact: Contact? = null
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var recentsAdapter: CallHistoryAdapter? = null
    private var currentRecentCall: RecentCall? = null
    private var currentRecentCallList: List<RecentCall>? = null
    private var recentsHelper = RecentsHelper(this)
    private var initShowAll = false
    private var showAll = false
    private var buttonBg = Color.WHITE

    private lateinit var mainAdapter: MainAdapter

    // Dynamic references to View (initialised in onBind)
    private var callHistoryList: com.goodwy.commons.views.MyRecyclerView? = null
    private var progressIndicator: com.google.android.material.progressindicator.CircularProgressIndicator? = null
    private var callHistoryListCount: com.goodwy.commons.views.MyTextView? = null
    private var callHistoryShowAll: android.widget.TextView? = null
    private var callHistoryPlaceholderContainer: View? = null
    private var callHistoryNumberContainer: View? = null
    private var callHistoryNumberTypeContainer: View? = null
    private var callHistoryFavoriteIcon: ImageView? = null
    private var callHistoryNumberType: com.goodwy.commons.views.MyTextView? = null
    private var callHistoryNumber: com.goodwy.commons.views.MyTextView? = null
    private var contactMessengersActionsHolder: android.view.ViewGroup? = null
    private var contactEmailsHolder: android.view.ViewGroup? = null
    private var contactEventsHolder: android.view.ViewGroup? = null
    private var defaultSimButtonContainer: View? = null
    private var defaultSim1Button: View? = null
    private var defaultSim2Button: View? = null
    private var defaultSim1Icon: ImageView? = null
    private var defaultSim2Icon: ImageView? = null
    private var defaultSim1Id: TextView? = null
    private var defaultSim2Id: TextView? = null
    private var callerNotesHolder: View? = null
    private var callerNotes: com.goodwy.commons.views.MyTextView? = null
    private var callerNotesIcon: ImageView? = null
    private var blockButton: androidx.appcompat.widget.AppCompatButton? = null

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupMainRecyclerView()

        setupEdgeToEdge(padBottomSystem = listOf(binding.mainRecyclerView))

        currentRecentCall = intent.getSerializableExtra(CURRENT_RECENT_CALL) as? RecentCall
        if (currentRecentCall == null) {
            finish()
            return
        }

        initButtons()

//        currentRecentCallList = intent.getSerializableExtra(CURRENT_RECENT_CALL_LIST) as? List<RecentCall>
//        currentRecentCallList?.let {
//            gotRecents(it)
//            initShowAll()
//        }
        // We wait for the View to be created, then load the data
        binding.mainRecyclerView.post {
            currentRecentCallList = intent.getSerializableExtra(CURRENT_RECENT_CALL_LIST) as? List<RecentCall>
            currentRecentCallList?.let {
                gotRecents(it)
                initShowAll()
            }
        }
    }

    private fun setupMainRecyclerView() {
        val sections = listOf(
            // 1. Call history section
            MainAdapter.Section(
                layoutId = R.layout.item_calls_section,
                viewType = SECTION_CALL_HISTORY,
                onBind = { view ->
                    callHistoryList = view.findViewById(R.id.callHistoryList)
                    progressIndicator = view.findViewById(R.id.progressIndicator)
                    callHistoryListCount = view.findViewById(R.id.callHistoryListCount)
                    callHistoryShowAll = view.findViewById(R.id.callHistoryShowAll)
                    callHistoryPlaceholderContainer = view.findViewById(R.id.placeholder_container)

                    callHistoryShowAll?.setOnClickListener { showAll() }
                    setupCallHistoryList()
                },
                isVisible = true
            ),

            // 2. Phone number section
            MainAdapter.Section(
                layoutId = R.layout.item_number_section,
                viewType = SECTION_PHONE_NUMBER,
                onBind = { view ->
                    callHistoryNumberContainer = view.findViewById(R.id.call_history_number_container)
                    callHistoryNumberTypeContainer = view.findViewById(R.id.call_history_number_type_container)
                    callHistoryFavoriteIcon = view.findViewById(R.id.call_history_favorite_icon)
                    callHistoryNumberType = view.findViewById(R.id.call_history_number_type)
                    callHistoryNumber = view.findViewById(R.id.call_history_number)

                    callHistoryNumberContainer?.setOnClickListener {
                        val call = currentRecentCall
                        if (call != null) {
                            makeCall(call)
                        }
                    }
                    callHistoryNumberContainer?.setOnLongClickListener {
                        copyToClipboard(callHistoryNumber?.text.toString())
                        true
                    }
                },
                isVisible = true
            ),

            // 3. Messenger section
            MainAdapter.Section(
                layoutId = R.layout.item_messengers_section,
                viewType = SECTION_MESSENGERS,
                onBind = { view ->
                    contactMessengersActionsHolder = view.findViewById(R.id.contact_messengers_actions_holder)
                },
                isVisible = true
            ),

            // 4. Email section
            MainAdapter.Section(
                layoutId = R.layout.item_emails_section,
                viewType = SECTION_EMAILS,
                onBind = { view ->
                    contactEmailsHolder = view.findViewById(R.id.contact_emails_holder)
                },
                isVisible = true
            ),

            // 5. Events section
            MainAdapter.Section(
                layoutId = R.layout.item_events_section,
                viewType = SECTION_EVENTS,
                onBind = { view ->
                    contactEventsHolder = view.findViewById(R.id.contact_events_holder)
                },
                isVisible = true
            ),

            // 6. SIM section
            MainAdapter.Section(
                layoutId = R.layout.item_sim_section,
                viewType = SECTION_SIM,
                onBind = { view ->
                    defaultSimButtonContainer = view.findViewById(R.id.defaultSimButtonContainer)
                    defaultSim1Button = view.findViewById(R.id.defaultSim1Button)
                    defaultSim2Button = view.findViewById(R.id.defaultSim2Button)
                    defaultSim1Icon = view.findViewById(R.id.defaultSim1Icon)
                    defaultSim2Icon = view.findViewById(R.id.defaultSim2Icon)
                    defaultSim1Id = view.findViewById(R.id.defaultSim1Id)
                    defaultSim2Id = view.findViewById(R.id.defaultSim2Id)
                },
                isVisible = true
            ),

            // 7. Notes section
            MainAdapter.Section(
                layoutId = R.layout.item_notes_section,
                viewType = SECTION_NOTES,
                onBind = { view ->
                    callerNotesHolder = view.findViewById(R.id.callerNotesHolder)
                    callerNotes = view.findViewById(R.id.callerNotes)
                    callerNotesIcon = view.findViewById(R.id.callerNotesIcon)

                    callerNotesHolder?.setOnClickListener {
                        changeNoteDialog(currentRecentCall!!.phoneNumber)
                    }
                    callerNotesHolder?.setOnLongClickListener {
                        val text = callerNotesHelper.getCallerNotes(currentRecentCall!!.phoneNumber)?.note
                        text?.let { note -> copyToClipboard(note) }
                        true
                    }
                },
                isVisible = true
            ),

            // 8. Lock button
            MainAdapter.Section(
                layoutId = R.layout.item_block_section,
                viewType = SECTION_BLOCK,
                onBind = { view ->
                    blockButton = view.findViewById(R.id.blockButton)

                    blockButton?.setOnClickListener {
                        askConfirmBlock()
                    }
                },
                isVisible = true
            ),
        )

        // Creating and configuring an adapter
        mainAdapter = MainAdapter(sections)
        binding.mainRecyclerView.apply {
            adapter = mainAdapter
            layoutManager = LinearLayoutManager(this@CallHistoryActivity)
        }
    }

    private fun updateSectionsVisibility() {
        // Call history
        mainAdapter.updateSectionVisibility(
            SECTION_CALL_HISTORY,
            currentRecentCallList?.isNotEmpty() == true
        )

        // Messengers
        val hasMessengers = (contactMessengersActionsHolder?.childCount ?: 0) > 0
        mainAdapter.updateSectionVisibility(SECTION_MESSENGERS, hasMessengers)

        // Email
        val hasEmails = (contactEmailsHolder?.childCount ?: 0) > 0
        mainAdapter.updateSectionVisibility(SECTION_EMAILS, hasEmails)

        // Events
        val hasEvents = (contactEventsHolder?.childCount ?: 0) > 0
        mainAdapter.updateSectionVisibility(SECTION_EVENTS, hasEvents)

        // SIM cards
        val showSIM = areMultipleSIMsAvailable() && currentRecentCall?.isUnknownNumber != true
        mainAdapter.updateSectionVisibility(SECTION_SIM, showSIM)
    }

    override fun onResume() {
        super.onResume()
        binding.mainRecyclerView.post {
            callHistoryPlaceholderContainer?.beGone()
            updateTextColors(binding.mainRecyclerView)
            buttonBg = if ((isLightTheme() || isGrayTheme()) && !isDynamicTheme()) Color.WHITE else getSurfaceColor()
            ensureBackgroundThread {
                initContact()
            }
            if (!initShowAll) refreshCallLog(false)
            updateButtons()
            setupMenu()
            setupCallerNotes()
        }
    }

    private fun initButtons() {
        val properPrimaryColor = getProperPrimaryColor()

        var drawableSMS = AppCompatResources.getDrawable(this, R.drawable.ic_messages)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, properPrimaryColor)
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        binding.oneButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)

        var drawableCall = AppCompatResources.getDrawable(this, R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, properPrimaryColor)
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        binding.twoButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)

        var drawableInfo = AppCompatResources.getDrawable(this, R.drawable.ic_videocam_vector)
        drawableInfo = DrawableCompat.wrap(drawableInfo!!)
        DrawableCompat.setTint(drawableInfo, properPrimaryColor)
        DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
        binding.threeButton.apply {
            setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
            alpha = 0.5f
            isEnabled = false
        }

        var drawableShare = AppCompatResources.getDrawable(this, R.drawable.ic_mail_vector)
        drawableShare = DrawableCompat.wrap(drawableShare!!)
        DrawableCompat.setTint(drawableShare, properPrimaryColor)
        DrawableCompat.setTintMode(drawableShare, PorterDuff.Mode.SRC_IN)
        binding.fourButton.apply {
            setCompoundDrawablesWithIntrinsicBounds(null, drawableShare, null, null)
            alpha = 0.5f
            isEnabled = false
        }

        binding.oneButton.setTextColor(properPrimaryColor)
        binding.twoButton.setTextColor(properPrimaryColor)
        binding.threeButton.setTextColor(properPrimaryColor)
        binding.fourButton.setTextColor(properPrimaryColor)
    }

    private fun initShowAll() {
        try {
            val recents = config.parseRecentCallsCache()
            val currentRecentCalls = recents.filter { it.phoneNumber == currentRecentCall!!.phoneNumber}

            if (currentRecentCallList != null) {
                if (currentRecentCalls.size > currentRecentCallList!!.size) {
                    callHistoryShowAll?.beVisible()
                    initShowAll = true
                }
            }
        } catch (_: Exception) { }
    }

    private fun showAll() {
        if (showAll) {
            // Action hide
            callHistoryShowAll?.beInvisible()
            progressIndicator?.show()
            currentRecentCallList?.let {
                gotRecents(it) {
                    showAll = false
                    callHistoryShowAll?.apply {
                        text = getString(R.string.all_g)
                        beVisible()
                    }
                }
            }
        } else {
            // Action show all
            callHistoryShowAll?.beInvisible()
            progressIndicator?.show()
            refreshCallLog(load = true, loadAll = false) {
                refreshCallLog(load = true, loadAll = true) {
                    runOnUiThread {
                        showAll = true
                        callHistoryShowAll?.apply {
                            text = getString(R.string.hide)
                            beVisible()
                        }
                    }
                }
            }
        }
    }

    private fun updateButtons() {
        val red = resources.getColor(R.color.red_missed, theme)
        val properPrimaryColor = getProperPrimaryColor()

        val phoneNumber = if (config.formatPhoneNumbers) currentRecentCall!!.phoneNumber.formatPhoneNumber() else currentRecentCall!!.phoneNumber

        callHistoryShowAll?.apply {
//                val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_chevron_down_vector)
//                drawable?.applyColorFilter(properPrimaryColor)
//                setDrawablesRelativeWithIntrinsicBounds(end = drawable)
            setTextColor(properPrimaryColor)
            setOnClickListener { showAll() }
        }

//        callHistoryNumberType?.beGone()
        callHistoryNumberType?.setTextColor(getProperTextColor())
        callHistoryNumberContainer?.setOnClickListener {
            val call = currentRecentCall
            if (call != null) {
                makeCall(call)
            }
        }
        callHistoryNumberContainer?.setOnLongClickListener {
            copyToClipboard(callHistoryNumber?.text.toString())
            true
        }
        callHistoryNumber?.apply {
            text = formatterUnicodeWrap(phoneNumber)
            setTextColor(properPrimaryColor)
        }

        if (isLightTheme() && !isDynamicTheme()) {
            val colorToWhite = getSurfaceColor()
            supportActionBar?.setBackgroundDrawable(colorToWhite.toDrawable())
            window.decorView.setBackgroundColor(colorToWhite)
            window.statusBarColor = colorToWhite
            //window.navigationBarColor = colorToWhite
            binding.contactActionsHolder.setBackgroundColor(colorToWhite)
            binding.collapsingToolbar.setBackgroundColor(colorToWhite)
        } else {
            val properBackgroundColor = getProperBackgroundColor()
            window.decorView.setBackgroundColor(properBackgroundColor)
            binding.contactActionsHolder.setBackgroundColor(properBackgroundColor)
            binding.collapsingToolbar.setBackgroundColor(properBackgroundColor)
        }

        arrayOf(
            binding.oneButton, binding.twoButton, binding.threeButton, binding.fourButton,
            callHistoryPlaceholderContainer, callHistoryList,
            callHistoryNumberContainer,
            contactMessengersActionsHolder,
            contactEmailsHolder,
            contactEventsHolder,
            callerNotesHolder,
            defaultSimButtonContainer,
            blockButton
        ).forEach {
            it?.background?.setTint(buttonBg)
        }

        if (isNumberBlocked(currentRecentCall!!.phoneNumber, getBlockedNumbers())) {
            blockButton?.text = getString(R.string.unblock_number)
            blockButton?.setTextColor(properPrimaryColor)
        } else {
            blockButton?.text = getString(R.string.block_number)
            blockButton?.setTextColor(red)
        }
    }

    private fun setupCallerNotes() {
        val callerNote = callerNotesHelper.getCallerNotes(currentRecentCall!!.phoneNumber)
        val note = callerNote?.note

        callerNotesIcon?.setColorFilter(getProperTextColor())
        callerNotesText(note)

        callerNotesHolder?.setOnClickListener {
            changeNoteDialog(currentRecentCall!!.phoneNumber)
        }
        callerNotesHolder?.setOnLongClickListener {
            val text = callerNotesHelper.getCallerNotes(currentRecentCall!!.phoneNumber)?.note
            text?.let { note -> copyToClipboard(note) }
            true
        }
    }

    private fun callerNotesText(note: String?) {
        callerNotes?.text = if (note.isNullOrEmpty()) getString(R.string.add_notes) else note
        callerNotes?.alpha = if (note.isNullOrEmpty()) 0.6f else 1f
    }

    private fun changeNoteDialog(number: String) {
        val callerNote = callerNotesHelper.getCallerNotes(number)
        ChangeTextDialog(
            activity = this@CallHistoryActivity,
            title = getString(R.string.add_notes) + " ($number)",
            currentText = callerNote?.note,
            maxLength = CALLER_NOTES_MAX_LENGTH,
            showNeutralButton = true,
            neutralTextRes = R.string.delete
        ) {
            if (it != "") {
                callerNotesHelper.addCallerNotes(number, it, callerNote) {
                    callerNotesText(it)
                }
            } else {
                callerNotesHelper.deleteCallerNotes(callerNote) {
                    callerNotesText(it)
                }
            }
        }
    }

    private fun setupMenu() {
        binding.callHistoryToolbar.menu.apply {
            updateMenuItemColors(this)

            findItem(R.id.delete).setOnMenuItemClickListener {
                askConfirmRemove()
                true
            }

            findItem(R.id.share).setOnMenuItemClickListener {
                launchShare()
                true
            }

            findItem(R.id.call_anonymously).setOnMenuItemClickListener {
                if (currentRecentCall != null) {
                    if (config.showWarningAnonymousCall) {
                        val text = String.format(getString(R.string.call_anonymously_warning), currentRecentCall!!.phoneNumber)
                        ConfirmationAdvancedDialog(
                            this@CallHistoryActivity,
                            text,
                            R.string.call_anonymously_warning,
                            R.string.ok,
                            R.string.do_not_show_again,
                            fromHtml = true
                        ) {
                            if (it) {
                                makeCall(currentRecentCall!!, "#31#")
                            } else {
                                config.showWarningAnonymousCall = false
                                makeCall(currentRecentCall!!, "#31#")
                            }
                        }
                    } else {
                        makeCall(currentRecentCall!!, "#31#")
                    }
                }
                true
            }
        }

        val properBackgroundColor = getProperBackgroundColor()
        val contrastColor = properBackgroundColor.getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor
        binding.callHistoryToolbar.apply {
            overflowIcon = resources.getColoredDrawableWithColor(R.drawable.ic_three_dots_vector, itemColor)
            setNavigationIconTint(itemColor)
            setNavigationOnClickListener {
                finish()
            }
        }
    }

    private fun getStarDrawable(on: Boolean) = AppCompatResources.getDrawable(this, if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun updateBackgroundHistory() {
        callHistoryList?.background?.setTint(buttonBg)
    }

    private fun refreshCallLog(load: Boolean, loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls { recents ->
            allRecentCall = recents
            val currentRecentCalls = recents.filter { it.phoneNumber == currentRecentCall!!.phoneNumber}

            if (load) {
                runOnUiThread {
                    if (recents.isEmpty() || currentRecentCalls.isEmpty()) {
                        callHistoryList?.beGone()
                        callHistoryPlaceholderContainer?.beVisible()
                        binding.callHistoryToolbar.menu.findItem(R.id.delete).isVisible = false
                    } else {
                        callHistoryList?.beVisible()
                        callHistoryPlaceholderContainer?.beGone()
                        binding.callHistoryToolbar.menu.findItem(R.id.delete).isVisible = true
                        gotRecents(currentRecentCalls)
                        updateBackgroundHistory()
                    }
                }
            } else {
                if (!initShowAll) {
                    if (currentRecentCallList != null) {
                        if (currentRecentCalls.size > currentRecentCallList!!.size) runOnUiThread { callHistoryShowAll?.beVisible() }
                    }
                }
            }

            callback?.invoke()
        }
    }

    private fun getRecentCalls(callback: (List<RecentCall>) -> Unit) {
        val queryCount = config.queryLimitRecent
        val existingRecentCalls = allRecentCall

        with(recentsHelper) {
            getRecentCalls(existingRecentCalls, queryCount) {
                callback(it)
            }
        }
    }

    private fun gotRecents(recents: List<RecentCall>, callback: (() -> Unit)? = null) {
        if (callHistoryList == null) return

        val currAdapter = callHistoryList?.adapter
        if (currAdapter == null) {
            recentsAdapter = CallHistoryAdapter(
                activity = this as SimpleActivity,
                recyclerView = callHistoryList!!,
                refreshItemsListener = null,
                hideTimeAtOtherDays = false,
                itemDelete = { deleted ->
                    allRecentCall = allRecentCall.filter { it !in deleted }
                },
                itemClick = {}
            )

            callHistoryList?.adapter = recentsAdapter
            recentsAdapter?.updateItems(recents)
            setupCallHistoryListCount(recents.size)
            mainAdapter.updateSectionVisibility(SECTION_CALL_HISTORY, recents.isNotEmpty())

            progressIndicator?.hide()
            if (this.areSystemAnimationsEnabled) {
                callHistoryList?.scheduleLayoutAnimation()
            }
        } else {
            recentsAdapter?.updateItems(recents)
            setupCallHistoryListCount(recents.size)
            mainAdapter.updateSectionVisibility(SECTION_CALL_HISTORY, recents.isNotEmpty())
            progressIndicator?.hide()
        }

        callback?.invoke()
    }

    private fun setupCallHistoryList() {
        callHistoryList?.apply {
            // 1. Disable standard nested behaviour
            isNestedScrollingEnabled = false

            // 2. Adding a custom touch handler
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                private var startY = 0f

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Maintain initial position
                            startY = e.y
                            // Block parental scrolling when touched
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaY = e.y - startY
                            val layoutManager = rv.layoutManager as LinearLayoutManager

                            // Checking whether we have reached the limits
                            val canScrollUp = layoutManager.findFirstVisibleItemPosition() > 0
                            val canScrollDown = layoutManager.findLastVisibleItemPosition() < layoutManager.itemCount - 1

                            // If we try to scroll up, but we are already at the top
                            if (deltaY > 0 && !canScrollUp) {
                                // We allow parents to scroll
                                parent.requestDisallowInterceptTouchEvent(false)
                            }
                            // If we try to scroll down, but we are already at the bottom
                            else if (deltaY < 0 && !canScrollDown) {
                                // We allow parents to scroll
                                parent.requestDisallowInterceptTouchEvent(false)
                            }
                            // Otherwise, we block parental scrolling.
                            else {
                                parent.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Returning control
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

                private val parent: RecyclerView
                    get() = binding.mainRecyclerView
            })

            // 3. Configure the adapter as usual
            layoutManager = MyLinearLayoutManager(this@CallHistoryActivity).apply {
                orientation = RecyclerView.VERTICAL
            }
        }
    }

    private fun setupCallHistoryListCount(count: Int) {
        callHistoryListCount?.apply {
            beVisibleIf(count > 6)
            text = String.format(getString(R.string.total_g), count.toString())
        }
    }

    private fun initContact() {
        var wasLookupKeyUsed = false
        var contactId: Int
        try {
            contactId = intent.getIntExtra(CONTACT_ID, 0)
        } catch (_: Exception) {
            return
        }
        if (contactId == 0 ) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        wasLookupKeyUsed = true
                    }

                    getLookupUriRawId(data)
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        if (contactId != 0 && !wasLookupKeyUsed) {

            handlePermission(PERMISSION_READ_CONTACTS) { granted ->
                val isPrivate = intent.getBooleanExtra(IS_PRIVATE, false)
                if (granted) contact =
                    ContactsHelper(this).getContactWithId(contactId, isPrivate)

                if (contact == null && isPrivate) {
                    ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { _ ->
                        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
                        contact = privateContacts.firstOrNull { it.id == contactId }
                    }
                }
            }

            if (contact == null) {
                runOnUiThread {
                    updateButton()
                    updateSectionsVisibility()
                }
            } else {
                getDuplicateContacts {
                    ContactsHelper(this).getContactSources {
                        contactSources = it
                        runOnUiThread {
                            setupMenuForContact()
                            setupVideoCallActions()
                            setupMessengersActions()
                            setupEmails()
                            setupEvents()
                            updateButton()
                            updateSectionsVisibility()
                        }
                    }
                }
            }
        } else {
            if (contact == null) {
                runOnUiThread {
                    setupMenuForNoContact()
                    updateButton()
                }
            } else {
                getDuplicateContacts {
                    runOnUiThread {
                        //setupFavorite()
                        setupVideoCallActions()
                        setupMessengersActions()
                        setupEmails()
                        setupEvents()
                        updateButton()
                    }
                }
            }
        }
        binding.mainRecyclerView.post {
            updateSectionsVisibility()
        }
    }

    private fun setupMenuForNoContact() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor

        val editMenu = binding.callHistoryToolbar.menu.findItem(R.id.edit)
        editMenu.isVisible = true
        val editIcon = resources.getColoredDrawableWithColor(R.drawable.ic_add_person_vector, itemColor)
        editMenu.icon = editIcon
        editMenu.setTitle(R.string.add_contact)
        editMenu.setOnMenuItemClickListener {
            addContact()
            true
        }
    }

    private fun addContact() {
        val phoneNumber = if (config.formatPhoneNumbers) currentRecentCall!!.phoneNumber.formatPhoneNumber() else currentRecentCall!!.phoneNumber
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun setupMenuForContact() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor

        val favoriteMenu = binding.callHistoryToolbar.menu.findItem(R.id.favorite)
        favoriteMenu.isVisible = true
        val favoriteIcon = getStarDrawable(contact!!.starred == 1)
        favoriteIcon!!.setTint(itemColor)
        favoriteMenu.icon = favoriteIcon
        favoriteMenu.setOnMenuItemClickListener {
            val newIsStarred = if (contact!!.starred == 1) 0 else 1
            ensureBackgroundThread {
                val contacts = arrayListOf(contact!!)
                if (newIsStarred == 1) {
                    ContactsHelper(this@CallHistoryActivity).addFavorites(contacts)
                } else {
                    ContactsHelper(this@CallHistoryActivity).removeFavorites(contacts)
                }
            }
            contact!!.starred = newIsStarred
            val favoriteIconNew = getStarDrawable(contact!!.starred == 1)
            favoriteIconNew!!.setTint(itemColor)
            favoriteMenu.icon = favoriteIconNew
            true
        }

        val editMenu = binding.callHistoryToolbar.menu.findItem(R.id.edit)
        editMenu.isVisible = true
        editMenu.setOnMenuItemClickListener {
            contact?.let { startContactEdit(it) }
            true
        }

        val openWithMenu = binding.callHistoryToolbar.menu.findItem(R.id.open_with)
        openWithMenu.isVisible = true
        openWithMenu.setOnMenuItemClickListener {
            openWith()
            true
        }
    }

    private fun getDuplicateContacts(callback: () -> Unit) {
        ContactsHelper(this).getDuplicatesOfContact(contact!!, false) { contacts ->
            ensureBackgroundThread {
                duplicateContacts.clear()
                val displayContactSources = getVisibleContactSources()
                contacts.filter { displayContactSources.contains(it.source) }.forEach {
                    val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                    if (duplicate != null) {
                        duplicateContacts.add(duplicate)
                    }
                }

                runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun setupVideoCallActions() {
        if (contact != null) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (_, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
            }

            val videoActions = arrayListOf<SocialAction>()
            for ((key, value) in sources) {

                if (value.lowercase() == WHATSAPP) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val whatsappVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(whatsappVideoActions)
                    }
                }

                if (value.lowercase() == SIGNAL) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val signalVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(signalVideoActions)
                    }
                }

                if (value.lowercase() == VIBER) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val viberVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(viberVideoActions)
                    }
                }

                if (value.lowercase() == TELEGRAM) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val telegramVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(telegramVideoActions)
                    }
                }

                if (value.lowercase() == THREEMA) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val threemaVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(threemaVideoActions)
                    }
                }
            }

            binding.threeButton.apply {
                alpha = if (videoActions.isNotEmpty()) 1f else 0.5f
                isEnabled = videoActions.isNotEmpty()
                setOnLongClickListener { toast(R.string.video_call); true; }
                if (videoActions.isNotEmpty()) setOnClickListener { showVideoCallAction(videoActions) }
            }
        }
    }

    private fun showVideoCallAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@CallHistoryActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (_: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (_: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupMessengersActions() {
        contactMessengersActionsHolder?.removeAllViews()
        var hasContent = false

        if (contact != null) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (_, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
            }
            for ((key, value) in sources) {
                // We are checking whether we support this messenger
                val supportedMessengers = listOf(WHATSAPP, SIGNAL, VIBER, TELEGRAM, THREEMA)
                if (value.lowercase() !in supportedMessengers) continue

                // Checking if there are any actions for this contact
                val actions = getSocialActions(key.id)
                if (actions.isEmpty()) continue

                // If you've made it this far, it means you have at least one messenger
                hasContent = true

                val isLastItem = sources.keys.last()
                ItemViewMessengersActionsBinding.inflate(layoutInflater, contactMessengersActionsHolder!!, false).apply {
                    contactMessengerActionName.text = if (value == "") getString(R.string.phone_storage) else value
                    val text = " (ID:" + key.source + ")"
                    contactMessengerActionAccount.text = text
                    val properTextColor = getProperTextColor()
                    contactMessengerActionName.setTextColor(properTextColor)
                    contactMessengerActionAccount.setTextColor(properTextColor)
                    contactMessengerActionHolder.setOnClickListener {
                        if (contactMessengerActionAccount.isVisible()) contactMessengerActionAccount.beGone()
                        else contactMessengerActionAccount.beVisible()
                    }
                    val properPrimaryColor = getProperPrimaryColor()
                    contactMessengerActionNumber.setTextColor(properPrimaryColor)
                    contactMessengersActionsHolder?.addView(root)

                    arrayOf(
                        contactMessengerActionMessageIcon, contactMessengerActionCallIcon, contactMessengerActionVideoIcon,
                    ).forEach {
                        it.background.setTint(properTextColor)
                        it.background.alpha = 40
                        it.setColorFilter(properPrimaryColor)
                    }

                    dividerContactMessengerAction.setBackgroundColor(properTextColor)
                    dividerContactMessengerAction.beGoneIf(isLastItem == key)

                    if (value.lowercase() == WHATSAPP) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == SIGNAL) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == VIBER) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            contactMessengerActionNumber.beGoneIf(contact!!.phoneNumbers.size > 1 && messageActions.isEmpty())
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == TELEGRAM) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(messageActions)
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(callActions)
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(videoActions)
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == THREEMA) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }
                }
            }
            //contactMessengersActionsHolder?.beVisible()
            mainAdapter.updateSectionVisibility(SECTION_MESSENGERS, hasContent)
            contactMessengersActionsHolder?.beVisibleIf(hasContent)
        } else {
            mainAdapter.updateSectionVisibility(SECTION_MESSENGERS, false)
            contactMessengersActionsHolder?.beGone()
        }
    }

    private fun showMessengerAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    if (actions.size > 1) {
//                        ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
//                            Intent(Intent.ACTION_VIEW).apply {
//                                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
//                                setDataAndType(uri, action.mimetype)
//                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                try {
//                                    startActivity(this)
//                                } catch (e: SecurityException) {
//                                    handlePermission(PERMISSION_CALL_PHONE) { success ->
//                                        if (success) {
//                                            startActivity(this)
//                                        } else {
//                                            toast(R.string.no_phone_call_permission)
//                                        }
//                                    }
//                                } catch (e: ActivityNotFoundException) {
//                                    toast(R.string.no_app_found)
//                                } catch (e: Exception) {
//                                    showErrorToast(e)
//                                }
//                            }
//                        }
                    } else {
                        val action = actions.first()
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (_: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (_: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        contactEmailsHolder?.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty()) {
            binding.fourButton.apply {
                alpha = 1f
                isEnabled = true
                setOnClickListener {
                    if (emails.size == 1) sendEmailIntent(emails.first().value)
                    else {
                        val items = java.util.ArrayList<RadioItem>()
                        emails.forEachIndexed { index, email ->
                            items.add(RadioItem(index, email.value))
                        }

                        RadioGroupDialog(this@CallHistoryActivity, items, R.string.email) {
                            sendEmailIntent(emails[it as Int].value)
                        }
                    }
                }
                setOnLongClickListener { toast(R.string.email); true; }
            }

            val isFirstItem = emails.first()
            val isLastItem = emails.last()
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, contactEmailsHolder!!, false).apply {
                    val email = it
                    contactEmailsHolder?.addView(root)
                    contactEmail.text = email.value
                    contactEmailType.text = getEmailTypeText(email.type, email.label)
                    val properTextColor = getProperTextColor()
                    contactEmailType.setTextColor(properTextColor)
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }

                    contactEmailIcon.beVisibleIf(isFirstItem == email)
                    contactEmailIcon.setColorFilter(properTextColor)
                    dividerContactEmail.setBackgroundColor(properTextColor)
                    dividerContactEmail.beGoneIf(isLastItem == email)
                    contactEmail.setTextColor(getProperPrimaryColor())
                }
            }
            mainAdapter.updateSectionVisibility(SECTION_EMAILS, true)
            contactEmailsHolder?.beVisible()
        } else {
            mainAdapter.updateSectionVisibility(SECTION_EMAILS, false)
            contactEmailsHolder?.beGone()
        }
    }

    private fun getEmailTypeText(type: Int, label: String): String {
        return if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> R.string.home
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> R.string.work
                    ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> R.string.mobile
                    else -> R.string.other
                }
            )
        }
    }

    private fun setupEvents() {
        if (contact != null) {
            var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

            duplicateContacts.forEach {
                events.addAll(it.events)
            }

            events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
            contactEventsHolder?.removeAllViews()

            if (events.isNotEmpty()) {
                val isFirstItem = events.first()
                val isLastItem = events.last()
                events.forEach {
                    ItemViewEventBinding.inflate(layoutInflater, contactEventsHolder!!, false).apply {
                        val event = it
                    contactEventsHolder?.addView(root)
                        it.value.getDateTimeFromDateString(true, contactEvent)
                        contactEventType.setText(getEventTextId(it.type))
                        val properTextColor = getProperTextColor()
                        contactEventType.setTextColor(properTextColor)
                        root.copyOnLongClick(it.value.getDateFormattedFromDateString(true))

                        contactEventIcon.beVisibleIf(isFirstItem == event)
                        contactEventIcon.setColorFilter(properTextColor)
                        dividerContactEvent.setBackgroundColor(properTextColor)
                        dividerContactEvent.beGoneIf(isLastItem == event)
                        contactEvent.setTextColor(getProperPrimaryColor())
                    }
                }
                mainAdapter.updateSectionVisibility(SECTION_EVENTS, true)
                contactEventsHolder?.beVisible()
            } else {
                mainAdapter.updateSectionVisibility(SECTION_EVENTS, false)
                contactEventsHolder?.beGone()
            }
        } else {
            mainAdapter.updateSectionVisibility(SECTION_EVENTS, false)
            contactEventsHolder?.beGone()
        }
    }

    private fun getEventTextId(type: Int) = when (type) {
        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> R.string.anniversary
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> R.string.birthday
        else -> R.string.other
    }

    private fun updateButton() {
        val call = currentRecentCall
        if (call != null) {
            if (call.phoneNumber == call.name
                || ((call.isABusinessCall() || call.isVoiceMail) && call.photoUri == "")
                || isDestroyed || isFinishing
            ) {
                val drawable = when {
                    call.isVoiceMail -> {
                        @SuppressLint("UseCompatLoadingForDrawables")
                        val drawableVoicemail = resources.getDrawable( R.drawable.placeholder_voicemail, theme)
                        if (baseConfig.useColoredContacts) {
                            val letterBackgroundColors = getLetterBackgroundColors()
                            val color = letterBackgroundColors[abs(call.name.hashCode()) % letterBackgroundColors.size].toInt()
                            (drawableVoicemail as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                        }
                        drawableVoicemail
                    }
                    call.isABusinessCall() -> SimpleContactsHelper(this@CallHistoryActivity).getColoredCompanyIcon(call.name)
                    else -> SimpleContactsHelper(this@CallHistoryActivity).getColoredContactIcon(call.name)
                }
                binding.topDetails.callHistoryImage.setImageDrawable(drawable)
            } else {
                if (!isFinishing && !isDestroyed) SimpleContactsHelper(this.applicationContext)
                    .loadContactImage(call.photoUri, binding.topDetails.callHistoryImage, call.name)
            }

            if (contact != null) {
                val contactPhoneNumber = contact!!.phoneNumbers
                    .firstOrNull { it.normalizedNumber == currentRecentCall!!.phoneNumber }
                if (contactPhoneNumber != null) {
                    callHistoryNumberTypeContainer?.beVisible()
                    callHistoryNumberType?.apply {
                        beVisible()
                        val phoneNumberType = contactPhoneNumber.type
                        val phoneNumberLabel = contactPhoneNumber.label
                        text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                    }
                    callHistoryFavoriteIcon?.apply {
                        beVisibleIf(contactPhoneNumber.isPrimary)
                        applyColorFilter(getProperTextColor())
                    }
                }

                arrayOf(
                    binding.topDetails.callHistoryImage, binding.topDetails.callHistoryName
                ).forEach {
                    it.setOnClickListener { viewContactInfo(contact!!) }
                }
                binding.topDetails.callHistoryImage.apply {
                    setOnLongClickListener { toast(R.string.contact_details); true; }
                    contentDescription = getString(R.string.contact_details)
                }
            } else {
                val countryOrVoiceMail =
                    if (currentRecentCall!!.isVoiceMail) getString(R.string.voicemail)
                    else currentRecentCall!!.phoneNumber.getCountryByNumber()
                if (countryOrVoiceMail != "") {
                    callHistoryNumberTypeContainer?.beVisible()
                    callHistoryNumberType?.apply {
                        beVisible()
                        text = countryOrVoiceMail
                    }
                }

                arrayOf(
                    binding.topDetails.callHistoryImage, binding.topDetails.callHistoryName
                ).forEach {
                    it.setOnClickListener {
                        addContact()
                    }
                }
                binding.topDetails.callHistoryImage.apply {
                    setOnLongClickListener { toast(R.string.add_contact); true; }
                    contentDescription = getString(R.string.add_contact)
                }
            }

            callHistoryPlaceholderContainer?.beGone()

            val name = call.name
            val formatPhoneNumbers = config.formatPhoneNumbers
            val nameToShow = if (name == call.phoneNumber && formatPhoneNumbers) {
                SpannableString(name.formatPhoneNumber())
            } else {
                SpannableString(name)
            }
            binding.topDetails.callHistoryName.apply {
                text = formatterUnicodeWrap(nameToShow.toString())
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(nameToShow.toString())
                    true
                }
            }

            binding.topDetails.callHistoryCompany.apply {
                val company = formatterUnicodeWrap(call.company)
                beVisibleIf(company != "" && !call.isABusinessCall())
                text = company
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(company)
                    true
                }
            }

            binding.topDetails.callHistoryJobPosition.apply {
                val jobPosition = formatterUnicodeWrap(call.jobPosition)
                beVisibleIf(jobPosition != "" && !call.isABusinessCall())
                text = jobPosition
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(jobPosition)
                    true
                }
            }

            binding.oneButton.apply {
                setOnClickListener {
                    launchSendSMSIntentRecommendation(call.phoneNumber)
                }
                setOnLongClickListener { toast(R.string.send_sms); true; }
            }

            binding.twoButton.apply {
                setOnClickListener {
                    makeCall(call)
                }
                setOnLongClickListener { toast(R.string.call); true; }
            }

            if (areMultipleSIMsAvailable() && !call.isUnknownNumber) {
                updateDefaultSIMButton(call)
                val phoneNumber = call.phoneNumber.replace("+", "%2B")
                val simList = getAvailableSIMCardLabels()
                defaultSim1Button?.setOnClickListener {
                    val sim1 = simList[0]
                    if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == sim1.handle) {
                        removeDefaultSIM()
                    } else {
                        config.saveCustomSIM("tel:$phoneNumber", sim1.handle)
                        toast(sim1.label)
                    }
                    updateDefaultSIMButton(call)
                    defaultSim1Button?.performHapticFeedback()
                }
                if (simList.size > 1) {
                    defaultSim2Button?.setOnClickListener {
                        val sim2 = simList[1]
                        if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == sim2.handle) {
                            removeDefaultSIM()
                        } else {
                            config.saveCustomSIM("tel:$phoneNumber", sim2.handle)
                            toast(sim2.label)
                        }
                        updateDefaultSIMButton(call)
                        defaultSim2Button?.performHapticFeedback()
                    }
                    mainAdapter.updateSectionVisibility(SECTION_SIM, true)
                    defaultSimButtonContainer?.beVisible()
                }  else {
                    mainAdapter.updateSectionVisibility(SECTION_SIM, false)
                    defaultSimButtonContainer?.beGone()
                }
            } else {
                mainAdapter.updateSectionVisibility(SECTION_SIM, false)
                defaultSimButtonContainer?.beGone()
            }

            blockButton?.apply {
                setOnClickListener {
                    askConfirmBlock()
                }
            }
        } else {
            callHistoryList?.beGone()
            callHistoryPlaceholderContainer?.beVisible()
            binding.callHistoryToolbar.menu.findItem(R.id.delete).isVisible = false
        }
    }

    private fun updateDefaultSIMButton(call: RecentCall) {
        val background = getProperTextColor()
        val phoneNumber = call.phoneNumber.replace("+","%2B")
        val simList = getAvailableSIMCardLabels()
        defaultSim1Icon?.background?.setTint(background)
        defaultSim1Icon?.background?.alpha = 40
        defaultSim1Icon?.setColorFilter(background.adjustAlpha(0.60f))
        defaultSim1Id?.setTextColor(Color.BLACK)

        defaultSim2Icon?.background?.setTint(background)
        defaultSim2Icon?.background?.alpha = 40
        defaultSim2Icon?.setColorFilter(background.adjustAlpha(0.60f))
        defaultSim2Id?.setTextColor(Color.BLACK)

        if (simList.size > 1) {
            val sim1 = simList[0].color
            val sim2 = simList[1].color

            if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == simList[0].handle && !call.isUnknownNumber) {
                defaultSim1Icon?.background?.setTint(sim1)
                defaultSim1Icon?.background?.alpha = 255
                defaultSim1Icon?.setColorFilter(Color.WHITE)
                defaultSim1Id?.setTextColor(sim1)
            }

            if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == simList[1].handle && !call.isUnknownNumber) {
                defaultSim2Icon?.background?.setTint(sim2)
                defaultSim2Icon?.background?.alpha = 255
                defaultSim2Icon?.setColorFilter(Color.WHITE)
                defaultSim2Id?.setTextColor(sim2)
            }
        }
    }

    private fun makeCall(call: RecentCall, prefix: String = "") {
        val phoneNumber = call.phoneNumber
        if (config.showCallConfirmation) {
            CallConfirmationDialog(this as SimpleActivity, call.name) {
                launchCallIntent("$prefix$phoneNumber", key = BuildConfig.RIGHT_APP_KEY)
            }
        } else {
            launchCallIntent("$prefix$phoneNumber", key = BuildConfig.RIGHT_APP_KEY)
        }
    }

    private fun removeDefaultSIM() {
        val phoneNumber = currentRecentCall!!.phoneNumber.replace("+","%2B")
        config.removeCustomSIM("tel:$phoneNumber")
    }

    private fun askConfirmBlock() {
        if (isDefaultDialer()) {
            val baseString = if (isNumberBlocked(currentRecentCall!!.phoneNumber, getBlockedNumbers())) {
                R.string.unblock_confirmation
            } else { R.string.block_confirmation }
            val question = String.format(resources.getString(baseString), currentRecentCall!!.phoneNumber)

            ConfirmationDialog(this, question) {
                blockNumbers()
            }
        } else toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
    }

    private fun blockNumbers() {
        config.needUpdateRecents = true
        val red = resources.getColor(R.color.red_missed, theme)
        runOnUiThread {
            if (isNumberBlocked(currentRecentCall!!.phoneNumber, getBlockedNumbers())) {
                deleteBlockedNumber(currentRecentCall!!.phoneNumber)
                blockButton?.text = getString(R.string.block_number)
                blockButton?.setTextColor(red)
            } else {
                addBlockedNumber(currentRecentCall!!.phoneNumber)
                blockButton?.text = getString(R.string.unblock_number)
                blockButton?.setTextColor(getProperPrimaryColor())
            }
        }
    }

    private fun askConfirmRemove() {
        val message =
            if (showAll) getString(R.string.clear_history_confirmation)
            else getString(R.string.remove_confirmation)
        ConfirmationDialog(this, message) {
            handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (currentRecentCall!!.phoneNumber.isEmpty()) {
            return
        }
        config.needUpdateRecents = true

        val callsToRemove =
            if (showAll) allRecentCall.filter { currentRecentCall!!.phoneNumber.contains(it.phoneNumber) } as ArrayList<RecentCall>
            else currentRecentCallList

        if (callsToRemove == null) {
            return
        }

        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.groupedCalls?.mapTo(idsToRemove) { call -> call.id }
        }

        RecentsHelper(this).removeRecentCalls(idsToRemove) {
            val callerNote = callerNotesHelper.getCallerNotes(currentRecentCall!!.phoneNumber)
            callerNotesHelper.deleteCallerNotes(callerNote)

            runOnUiThread {
                onBackPressed()
            }
        }
    }

//    private fun finishActMode() {
//        recentsAdapter?.finishActMode()
//    }

    private fun viewContactInfo(contact: Contact) {
        this.startContactDetailsIntentRecommendation(contact)
    }

    private fun launchShare() {
        val text = currentRecentCall!!.phoneNumber
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, text)
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(R.string.invite_via)))
        }
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }

    private fun openWith() {
        if (contact != null) {
            val uri = getContactPublicUri(contact!!)
            launchViewContactIntent(uri)
        }
    }
}

// hide private contacts from recent calls
//private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
//    return if (shouldHide) {
//        filterNot { recent ->
//            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
//            recent.phoneNumber in privateNumbers
//        }
//    } else {
//        this
//    }
//}

//private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
//    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
//    return map { recent ->
//        if (recent.phoneNumber == recent.name) {
//            val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
//            val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
//
//            when {
//                privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
//                contact != null -> recent.copy(name = contact.getNameToDisplay())
//                else -> recent
//            }
//        } else {
//            recent
//        }
//    } as ArrayList
//}
