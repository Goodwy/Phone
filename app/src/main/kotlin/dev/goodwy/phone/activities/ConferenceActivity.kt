package dev.goodwy.phone.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.commons.helpers.NavigationIcon
import dev.goodwy.phone.adapters.ConferenceCallsAdapter
import dev.goodwy.phone.databinding.ActivityConferenceBinding
import dev.goodwy.phone.helpers.CallManager
import dev.goodwy.phone.helpers.NoCall

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(conferenceList))
            setupMaterialScrollListener(conferenceList, conferenceAppbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conferenceAppbar, NavigationIcon.Arrow)
    }

    override fun onBackPressedCompat(): Boolean {
        return when (CallManager.getPhoneState()) {
            NoCall -> {
                finishAndRemoveTask()
                true
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                true
            }
        }
    }
}
