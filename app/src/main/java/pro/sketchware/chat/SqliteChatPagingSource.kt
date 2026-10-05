package pro.sketchware.chat

import androidx.lifecycle.LiveData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.liveData

/** Paging 3 source backed by the app-private SQLite chat database. */
internal class SqliteChatPagingSource(
    private val storage: SqliteChatStorage,
    private val projectId: String,
    private val threadId: String
) : PagingSource<Int, ChatPagingItem>() {
    private val invalidationListener = Runnable { invalidate() }

    init {
        storage.addInvalidationListener(invalidationListener)
        registerInvalidatedCallback {
            storage.removeInvalidationListener(invalidationListener)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatPagingItem> {
        return try {
            val showAds = true
            val requestedStart = params.key
            val window = storage.loadPagingWindow(
                projectId,
                threadId,
                requestedStart ?: -1,
                params.loadSize,
                showAds,
                3 /* conversation turns between ads */
            )
            val start = window.start
            val end = start + window.items.size
            LoadResult.Page(
                data = window.items,
                prevKey = if (start <= 0) null else maxOf(0, start - params.loadSize),
                nextKey = if (end >= window.totalCount) null else end,
                itemsBefore = start,
                itemsAfter = maxOf(0, window.totalCount - end)
            )
        } catch (error: Throwable) {
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ChatPagingItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)
    }
}

object ChatPagingFactory {
    private const val PAGE_SIZE = 30

    @JvmStatic
    fun create(
        storage: SqliteChatStorage,
        projectId: String,
        threadId: String
    ): LiveData<PagingData<ChatPagingItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = 10,
            enablePlaceholders = false,
            initialLoadSize = PAGE_SIZE
        ),
        pagingSourceFactory = { SqliteChatPagingSource(storage, projectId, threadId) }
    ).liveData
}
