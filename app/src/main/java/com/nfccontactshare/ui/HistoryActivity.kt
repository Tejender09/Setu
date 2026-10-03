package com.nfccontactshare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nfccontactshare.R
import com.nfccontactshare.data.PrefsManager
import com.nfccontactshare.databinding.ActivityHistoryBinding
import com.nfccontactshare.model.ContactCard

/**
 * History screen showing all previously exchanged contacts.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        adapter = HistoryAdapter()

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        supportActionBar?.title = "Exchange History"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadHistory()

        binding.btnClearHistory.setOnClickListener {
            prefsManager.clearHistory()
            loadHistory()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadHistory() {
        val history = prefsManager.getHistory()
        adapter.submitList(history)
        binding.tvEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class HistoryAdapter : ListAdapter<ContactCard, HistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvHistoryName)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvHistoryPhone)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvHistoryEmail)

        fun bind(card: ContactCard) {
            tvName.text = card.name
            tvPhone.text = card.phone
            tvEmail.text = if (card.email.isNotEmpty()) card.email else "No email"
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ContactCard>() {
            override fun areItemsTheSame(a: ContactCard, b: ContactCard) = a.phone == b.phone
            override fun areContentsTheSame(a: ContactCard, b: ContactCard) = a == b
        }
    }
}
