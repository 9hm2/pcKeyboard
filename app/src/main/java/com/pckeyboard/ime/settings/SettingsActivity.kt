package com.pckeyboard.ime.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pckeyboard.ime.databinding.ActivitySettingsBinding
import com.pckeyboard.ime.editor.ThemeEditorActivity
import com.pckeyboard.ime.theme.KeyboardTheme
import com.pckeyboard.ime.theme.ThemeRepository

/**
 * Settings: pick the active theme, manage custom themes, launch the editor.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: ThemeRepository
    private lateinit var adapter: ThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ThemeRepository(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ThemeAdapter(
            onSelect = {
                repo.selectTheme(it.id)
                adapter.selectedId = it.id
                adapter.notifyDataSetChanged()
            },
            onEdit = { editTheme(it) },
            onDelete = {
                repo.deleteCustomTheme(it.id)
                reload()
            },
            initialSelectedId = repo.getSelectedTheme().id
        )
        binding.themeList.layoutManager = LinearLayoutManager(this)
        binding.themeList.adapter = adapter

        binding.btnNewTheme.setOnClickListener {
            startActivity(Intent(this, ThemeEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun reload() {
        adapter.submit(repo.allThemes())
        adapter.selectedId = repo.getSelectedTheme().id
    }

    private fun editTheme(t: KeyboardTheme) {
        startActivity(
            Intent(this, ThemeEditorActivity::class.java)
                .putExtra(ThemeEditorActivity.EXTRA_THEME_ID, t.id)
        )
    }
}
