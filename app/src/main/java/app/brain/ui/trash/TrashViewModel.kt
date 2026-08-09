package app.brain.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.RecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val recordDao: RecordDao,
) : ViewModel() {

    val trashed: StateFlow<List<RecordEntity>> = recordDao.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: String) {
        viewModelScope.launch { recordDao.restore(id) }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch { recordDao.deletePermanently(id) }
    }
}