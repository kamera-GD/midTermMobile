
package com.example.thanhhoa_offlinetask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

import com.example.thanhhoa_offlinetask.data.local.AppDatabase
import com.example.thanhhoa_offlinetask.data.local.Task
import com.example.thanhhoa_offlinetask.data.remote.NetworkUtils
import com.example.thanhhoa_offlinetask.data.remote.RetrofitClient
import com.example.thanhhoa_offlinetask.data.repository.SyncManager
import com.example.thanhhoa_offlinetask.data.repository.TaskRepository
import com.example.thanhhoa_offlinetask.ui.TaskViewModel
import com.example.thanhhoa_offlinetask.ui.TaskViewModelFactory
import com.example.thanhhoa_offlinetask.ui.theme.ThanhHoa_OfflineTaskTheme
import com.example.thanhhoa_offlinetask.worker.SyncScheduler

class MainActivity : ComponentActivity() {

    private val database by lazy {
        AppDatabase.getDatabase(applicationContext)
    }

    private val repository by lazy {
        TaskRepository(database.taskDao())
    }

    private val syncManager by lazy {
        SyncManager(
            database.taskDao(),
            RetrofitClient.apiService,
            isOnlineProvider = { NetworkUtils.isNetworkAvailable(applicationContext) }
        )
    }

    private val viewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(
            repository,
            syncManager,
            applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Đăng ký đồng bộ định kỳ bằng WorkManager
        SyncScheduler.schedule(applicationContext)

        // Yêu cầu đồng bộ một lần khi có Internet
        SyncScheduler.syncWhenConnected(applicationContext)

        enableEdgeToEdge()

        setContent {
            ThanhHoa_OfflineTaskTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TaskScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var taskTitle by remember {
        mutableStateOf("")
    }

    var editingTask by remember {
        mutableStateOf<Task?>(null)
    }

    var editTitle by remember {
        mutableStateOf("")
    }

    var deletingTask by remember {
        mutableStateOf<Task?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "Offline Task App",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Quản lý công việc ngoại tuyến",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Đồng bộ thủ công
        Button(
            onClick = {
                viewModel.syncTasks()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        ) {
            Text(
                if (isSyncing) {
                    "Đang đồng bộ..."
                } else {
                    "Đồng bộ ngay"
                }
            )
        }

        // Hiển thị kết quả đồng bộ
        if (syncMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = syncMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Nhập tên công việc
        OutlinedTextField(
            value = taskTitle,
            onValueChange = {
                taskTitle = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Tên công việc")
            },
            placeholder = {
                Text("Ví dụ: Học Kotlin")
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Thêm công việc
        Button(
            onClick = {
                viewModel.addTask(taskTitle)
                taskTitle = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = taskTitle.isNotBlank()
        ) {
            Text("Thêm công việc")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Danh sách công việc (${tasks.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Text(
                text = "Chưa có công việc nào. Hãy thêm công việc đầu tiên!",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = tasks,
                    key = { it.id }
                ) { task ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = {
                                viewModel.toggleCompleted(task)
                            }
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            val statusText = when (task.syncStatus) {
                                "PENDING" -> "⏳ Chờ đồng bộ"
                                "SYNCING" -> "🔄 Đang đồng bộ"
                                "SYNCED" -> "✅ Đã đồng bộ"
                                "FAILED" -> "❌ Đồng bộ thất bại"
                                else -> "Không xác định"
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = {
                                editingTask = task
                                editTitle = task.title
                            }
                        ) {
                            Text("Sửa")
                        }

                        TextButton(
                            onClick = {
                                deletingTask = task
                            }
                        ) {
                            Text(
                                text = "Xóa",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    HorizontalDivider()
                }
            }
        }
    }

    // Hộp thoại sửa công việc
    if (editingTask != null) {
        AlertDialog(
            onDismissRequest = {
                editingTask = null
            },
            title = {
                Text("Sửa công việc")
            },
            text = {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = {
                        editTitle = it
                    },
                    label = {
                        Text("Tên công việc")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingTask?.let { task ->
                            viewModel.updateTask(
                                task = task,
                                newTitle = editTitle
                            )
                        }
                        editingTask = null
                    },
                    enabled = editTitle.isNotBlank()
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingTask = null
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    // Hộp thoại xác nhận xóa
    if (deletingTask != null) {
        AlertDialog(
            onDismissRequest = {
                deletingTask = null
            },
            title = {
                Text("Xác nhận xóa")
            },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa công việc " +
                            "\"${deletingTask?.title}\" không?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingTask?.let { task ->
                            viewModel.deleteTask(task)
                        }
                        deletingTask = null
                    }
                ) {
                    Text(
                        text = "Xóa",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletingTask = null
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}
