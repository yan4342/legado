package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.databinding.ActivityReadRecordBinding
import io.legado.app.databinding.ItemReadRecordBinding
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.cardBackgroundColor
import io.legado.app.lib.theme.colorSurfaceContainer
import io.legado.app.lib.theme.popupBackgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.observeEvent
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getInt
import io.legado.app.utils.putInt
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReadRecordActivity : BaseActivity<ActivityReadRecordBinding>() {

    private val adapter by lazy { RecordAdapter(this) }
    private var sortMode
        get() = LocalConfig.getInt("readRecordSort")
        set(value) {
            LocalConfig.putInt("readRecordSort", value)
        }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    override val binding by viewBinding(ActivityReadRecordBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initAllTime()
        initData()
        initChart()
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read_record, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_enable_record)?.isChecked = AppConfig.enableReadRecord
        when (sortMode) {
            1 -> menu.findItem(R.id.menu_sort_read_long)?.isChecked = true
            2 -> menu.findItem(R.id.menu_sort_read_time)?.isChecked = true
            else -> menu.findItem(R.id.menu_sort_name)?.isChecked = true
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sort_name -> {
                sortMode = 0
                item.isChecked = true
                initData()
            }

            R.id.menu_sort_read_long -> {
                sortMode = 1
                item.isChecked = true
                initData()
            }

            R.id.menu_sort_read_time -> {
                sortMode = 2
                item.isChecked = true
                initData()
            }

            R.id.menu_enable_record -> {
                AppConfig.enableReadRecord = !item.isChecked
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        initSearchView()
        binding.tvBookName.setText(R.string.all_read_time)
        // Hide the clear-all button from the summary row; clear is now per-item only
        binding.tvRemove.visibility = android.view.View.GONE
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        // Card background colors from theme
        val cardBg = cardBackgroundColor
        val isCardLight = ColorUtils.isColorLight(cardBg)
        val cardPrimaryText = if (isCardLight) {
            getCompatColor(R.color.md_light_primary_text)
        } else {
            getCompatColor(R.color.md_dark_primary_text)
        }
        val cardSecondaryText = if (isCardLight) {
            getCompatColor(R.color.md_light_secondary)
        } else {
            getCompatColor(R.color.md_dark_secondary)
        }
        // Divider colors based on card background
        val dividerColor = if (isCardLight) {
            0x1F000000 // 12% black for light cards
        } else {
            0x1FFFFFFF // 12% white for dark cards
        }
        binding.cardChartInclude.divider1.setBackgroundColor(dividerColor)
        binding.cardChartInclude.divider2.setBackgroundColor(dividerColor)
        binding.cardChartInclude.cardChart.setCardBackgroundColor(cardBg)
        binding.cardSummary.setCardBackgroundColor(cardBg)
        // TabLayout 背景色与卡片一致
        binding.cardChartInclude.tabChartType.setBackgroundColor(cardBg)
        // Tab 指示器颜色
        binding.cardChartInclude.tabChartType.setSelectedTabIndicatorColor(accentColor)
        // Stats text colors based on card background
        binding.cardChartInclude.tvTodayTime.setTextColor(cardPrimaryText)
        binding.cardChartInclude.tvTodayTimeLabel.setTextColor(cardSecondaryText)
        binding.cardChartInclude.tvConsecutiveDays.setTextColor(cardPrimaryText)
        binding.cardChartInclude.tvConsecutiveDaysLabel.setTextColor(cardSecondaryText)
        binding.cardChartInclude.tvTotalBooks.setTextColor(cardPrimaryText)
        binding.cardChartInclude.tvTotalBooksLabel.setTextColor(cardSecondaryText)
        // Summary card text colors
        binding.tvBookName.setTextColor(cardPrimaryText)
        binding.tvReadingTime.setTextColor(cardPrimaryText)
        binding.tvRemove.setTextColor(cardPrimaryText)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                initData(newText)
                return false
            }
        })
    }

    private fun initAllTime() {
        lifecycleScope.launch {
            val allTime = withContext(IO) {
                appDb.readRecordDao.allTime
            }
            binding.tvReadingTime.text = formatDuring(allTime)
        }
    }

    private fun initData(searchKey: String? = null) {
        lifecycleScope.launch {
            val readRecords = withContext(IO) {
                appDb.readRecordDao.search(searchKey ?: "").let { records ->
                    when (sortMode) {
                        1 -> records.sortedByDescending { it.readTime }
                        2 -> records.sortedByDescending { it.lastRead }
                        else -> records.sortedWith { o1, o2 ->
                            o1.bookName.cnCompare(o2.bookName)
                        }
                    }
                }
            }
            adapter.setItems(readRecords)
        }
    }

    @Suppress("SetTextI18n")
    private fun initChart() {
        lifecycleScope.launch {
            try {
            val cal = Calendar.getInstance()
            val todayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = todayFormat.format(cal.time)

            // Date range: last 365 days
            cal.add(Calendar.DAY_OF_YEAR, -364)
            val startDate = todayFormat.format(cal.time)

            val dailyRecords = withContext(IO) {
                appDb.dailyReadRecordDao.sumDailyByDateRange(startDate, today)
            }
            val heatmapData = dailyRecords.associate { it.date to it.readTime }

            // 排行榜使用全时段总阅读时长数据（来自 readRecord 表）
            val topBooks = withContext(IO) {
                appDb.readRecordDao.allShow
                    .sortedByDescending { it.readTime }
                    .take(20)
            }

            // Today's reading time
            val todayTime = withContext(IO) {
                appDb.dailyReadRecordDao.sumByDateRange(today, today)
            }
            binding.cardChartInclude.tvTodayTime.text = formatDuring(todayTime)

            // Consecutive days
            val consecutiveDays = withContext(IO) {
                var count = 0
                val checkCal = Calendar.getInstance()
                val checkFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                while (true) {
                    val checkDate = checkFormat.format(checkCal.time)
                    val dayTime = appDb.dailyReadRecordDao.sumByDateRange(checkDate, checkDate)
                    if (dayTime > 0) {
                        count++
                        checkCal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }
                count
            }
            binding.cardChartInclude.tvConsecutiveDays.text = "${consecutiveDays}天"

            // Total books count
            val totalBooks = withContext(IO) {
                appDb.readRecordDao.allShow.size
            }
            binding.cardChartInclude.tvTotalBooks.text = "${totalBooks}本"

            // Heatmap
            binding.cardChartInclude.heatmapView.setData(heatmapData)
            binding.cardChartInclude.heatmapView.onDayClick = { date, readTime ->
                // Tooltip is shown by the view itself
            }

            // Bar chart
            val barItems = topBooks.filter { it.bookName.isNotBlank() }.map {
                ReadBarChartView.BarItem(it.bookName, it.readTime)
            }
            binding.cardChartInclude.barChartView.setData(barItems)
            binding.cardChartInclude.barChartView.onItemClick = { bookName ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        appDb.bookDao.findByName(bookName).firstOrNull()
                    }
                    if (book == null) {
                        SearchActivity.start(this@ReadRecordActivity, bookName)
                    } else {
                        startActivityForBook(book)
                    }
                }
            }

            // Tab text color
            val textAccentKey = if (AppConfig.isNightTheme) PreferKey.cNTextAccent else PreferKey.cTextAccent
            val textAccentColor = getPrefInt(textAccentKey)
            if (textAccentColor != 0) {
                binding.cardChartInclude.tabChartType.setTabTextColors(
                    textAccentColor and 0x99FFFFFF.toInt(),
                    textAccentColor
                )
            }

            // Tab switch
            binding.cardChartInclude.tabChartType.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        when (tab?.position) {
                            0 -> {
                                binding.cardChartInclude.heatmapView.visibility = View.VISIBLE
                                binding.cardChartInclude.barChartView.visibility = View.GONE
                            }
                            1 -> {
                                binding.cardChartInclude.heatmapView.visibility = View.GONE
                                binding.cardChartInclude.barChartView.visibility = View.VISIBLE
                            }
                        }
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                }
            )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class RecordAdapter(context: Context) :
        RecyclerAdapter<ReadRecordShow, ItemReadRecordBinding>(context) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        override fun getViewBinding(parent: ViewGroup): ItemReadRecordBinding {
            return ItemReadRecordBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadRecordBinding,
            item: ReadRecordShow,
            payloads: MutableList<Any>,
        ) {
            // Read colors dynamically so they update with day/night theme changes
            val cardBg = context.cardBackgroundColor
            val isCardLight = ColorUtils.isColorLight(cardBg)
            val cardPrimaryText = if (isCardLight) {
                context.getCompatColor(R.color.md_light_primary_text)
            } else {
                context.getCompatColor(R.color.md_dark_primary_text)
            }
            val cardSecondaryText = if (isCardLight) {
                context.getCompatColor(R.color.md_light_secondary)
            } else {
                context.getCompatColor(R.color.md_dark_secondary)
            }
            (holder.itemView as? com.google.android.material.card.MaterialCardView)
                ?.setCardBackgroundColor(cardBg)
            binding.apply {
                tvBookName.text = item.bookName
                tvBookName.setTextColor(cardPrimaryText)
                tvReadingTimeTag.setTextColor(cardSecondaryText)
                tvReadingTime.setTextColor(cardSecondaryText)
                tvLastReadTimeTag.setTextColor(cardSecondaryText)
                tvLastReadTime.setTextColor(cardSecondaryText)
                tvRemove.setTextColor(cardPrimaryText)
                tvReadingTime.text = formatDuring(item.readTime)
                if (item.lastRead > 0) {
                    tvLastReadTime.text = dateFormat.format(item.lastRead)
                } else {
                    tvLastReadTime.text = ""
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemReadRecordBinding) {
            binding.apply {
                root.setOnClickListener {
                    val item = getItem(holder.layoutPosition) ?: return@setOnClickListener
                    lifecycleScope.launch {
                        val book = withContext(IO) {
                            appDb.bookDao.findByName(item.bookName).firstOrNull()
                        }
                        if (book == null) {
                            SearchActivity.start(this@ReadRecordActivity, item.bookName)
                        } else {
                            startActivityForBook(book)
                        }
                    }
                }
                root.setOnLongClickListener {
                    val item = getItem(holder.layoutPosition) ?: return@setOnLongClickListener true
                    lifecycleScope.launch {
                        val book = withContext(IO) {
                            appDb.bookDao.findByName(item.bookName).firstOrNull()
                        }
                        if (book != null) {
                            val chapterNum = book.durChapterIndex + 1
                            val title = book.durChapterTitle
                            val msg = if (!title.isNullOrBlank()) {
                                getString(R.string.chapter) + " $chapterNum $title"
                            } else {
                                getString(R.string.chapter) + " $chapterNum"
                            }
                            toastOnUi(msg)
                        }
                    }
                    true
                }
                tvRemove.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { item ->
                        sureDelAlert(item)
                    }
                }
            }
        }

        private fun sureDelAlert(item: ReadRecordShow) {
            alert(R.string.delete) {
                setMessage(getString(R.string.sure_del_any, item.bookName))
                yesButton {
                    appDb.readRecordDao.deleteByName(item.bookName)
                    appDb.dailyReadRecordDao.deleteByBook(item.bookName)
                    initData()
                    initChart()
                }
                noButton()
            }
        }

    }

    fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val d = if (days > 0) "${days}天" else ""
        val h = if (hours > 0) "${hours}小时" else ""
        val m = if (minutes > 0) "${minutes}分钟" else ""
        val s = if (seconds > 0) "${seconds}秒" else ""
        var time = "$d$h$m$s"
        if (time.isBlank()) {
            time = "0秒"
        }
        return time
    }

}