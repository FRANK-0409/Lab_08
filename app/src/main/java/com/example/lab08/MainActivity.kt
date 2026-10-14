package com.example.lab08

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.example.lab08.ui.theme.Lab08Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Crear canal de notificaciones (Característica 6)
        createNotificationChannel(this)

        val db = Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            "task_db"
        ).fallbackToDestructiveMigration().build()

        val taskDao = db.taskDao()
        val viewModel = TaskViewModel(taskDao)

        setContent {
            Lab08Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        FullTaskAppScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorio de Tareas"
            val descriptionText = "Notificaciones de recordatorio para tareas"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("task_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// 6. Enviar Notificación Local (+2 pts)
@android.annotation.SuppressLint("MissingPermission")
fun sendTaskNotification(context: Context, title: String, message: String) {
    try {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "task_channel")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullTaskAppScreen(viewModel: TaskViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val categories = listOf("Todas", "General", "Trabajo", "Personal", "Estudio")

    // 1. Diálogo Editar Tarea
    if (taskToEdit != null) {
        TaskFormDialog(
            title = "Editar Tarea",
            initialDescription = taskToEdit?.description ?: "",
            initialPriority = taskToEdit?.priority ?: "MEDIA",
            initialCategory = taskToEdit?.category ?: "General",
            initialRecurrence = taskToEdit?.recurrence ?: "NINGUNA",
            onDismiss = { taskToEdit = null },
            onConfirm = { desc, prio, cat, rec ->
                taskToEdit?.let { task ->
                    viewModel.editTask(task, desc, prio, cat, rec)
                }
                taskToEdit = null
            }
        )
    }

    // Diálogo Agregar Tarea
    if (showAddDialog) {
        TaskFormDialog(
            title = "Nueva Tarea",
            initialDescription = "",
            initialPriority = "MEDIA",
            initialCategory = "General",
            initialRecurrence = "NINGUNA",
            onDismiss = { showAddDialog = false },
            onConfirm = { desc, prio, cat, rec ->
                viewModel.addTask(desc, prio, cat, rec)
                showAddDialog = false
                // Trigger notificación (Característica 6)
                sendTaskNotification(context, "Nueva Tarea Creada", desc)
            }
        )
    }

    // Diálogo Borrar Todo
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("¿Eliminar todas las tareas?") },
            text = { Text("Esta acción borrará permanentemente todas las tareas de la base de datos.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllTasks()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Encabezado principal con Nube y Menú
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mis Tareas",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Row {
                // 10. Sincronización en la Nube (+2 pts)
                IconButton(
                    onClick = {
                        viewModel.syncWithCloud {
                            Toast.makeText(context, "¡Sincronizado con la nube!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sincronizar en la nube", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // 5. Menú de Ordenamiento
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Ordenar")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Más recientes") },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.DATE_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Por Nombre (A-Z)") },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.NAME_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Por Prioridad (Alta primero)") },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.PRIORITY_HIGH)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 4. Barra de Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Buscar tareas...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // 3. Chips de Estado (Todas / Pendientes / Completadas)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == TaskFilter.ALL,
                onClick = { viewModel.setFilter(TaskFilter.ALL) },
                label = { Text("Todas") }
            )
            FilterChip(
                selected = currentFilter == TaskFilter.PENDING,
                onClick = { viewModel.setFilter(TaskFilter.PENDING) },
                label = { Text("Pendientes") }
            )
            FilterChip(
                selected = currentFilter == TaskFilter.COMPLETED,
                onClick = { viewModel.setFilter(TaskFilter.COMPLETED) },
                label = { Text("Completadas") }
            )
        }

        // 9. Chips de Categorías
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(categories) { cat ->
                SuggestionChip(
                    onClick = { viewModel.setCategory(cat) },
                    label = { Text(cat) },
                    colors = if (selectedCategory == cat) {
                        SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else SuggestionChipDefaults.suggestionChipColors()
                )
            }
        }

        // Lista de Tareas
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No se encontraron tareas" else "¡No hay tareas!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCardFull(
                        task = task,
                        onToggleCompletion = { viewModel.toggleTaskCompletion(task) },
                        onEdit = { taskToEdit = task },
                        onDelete = { viewModel.deleteTask(task) },
                        onNotify = {
                            sendTaskNotification(context, "Recordatorio: ${task.description}", "Prioridad: ${task.priority}")
                            Toast.makeText(context, "Recordatorio enviado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botones Inferiores: Agregar + Vaciar Todo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nueva Tarea")
            }

            OutlinedButton(
                onClick = { showDeleteAllDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// Tarjeta Completa de Tarea
@Composable
fun TaskCardFull(
    task: Task,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNotify: () -> Unit
) {
    val cardBackgroundColor by animateColorAsState(
        if (task.isCompleted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    )

    val priorityColor = when (task.priority.uppercase()) {
        "ALTA" -> Color(0xFFE53935)
        "MEDIA" -> Color(0xFFFB8C00)
        "BAJA" -> Color(0xFF43A047)
        else -> MaterialTheme.colorScheme.primary
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBackgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggleCompletion() }
                )

                Text(
                    text = task.description,
                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 10. Indicador de Nube
                Icon(
                    imageVector = if (task.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (task.isSynced) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Badges e Información Adicional
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp, bottom = 4.dp)
            ) {
                // 7. Badge de Prioridad
                AssistChip(
                    onClick = {},
                    label = { Text(task.priority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = priorityColor) },
                    modifier = Modifier.height(24.dp)
                )

                // 9. Badge de Categoría
                AssistChip(
                    onClick = {},
                    label = { Text(task.category, fontSize = 11.sp) },
                    modifier = Modifier.height(24.dp)
                )

                // 8. Recurrencia (si tiene)
                if (task.recurrence != "NINGUNA") {
                    AssistChip(
                        onClick = {},
                        label = { Text("🔄 ${task.recurrence}", fontSize = 11.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // Botones de acción rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // 6. Botón Recordatorio Notificación
                IconButton(onClick = onNotify, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Notifications, contentDescription = "Recordatorio", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
                // 1. Botón Editar
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                // 2. Botón Eliminar
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Diálogo reutilizable para Formulario de Tarea (Crear y Editar)
@Composable
fun TaskFormDialog(
    title: String,
    initialDescription: String,
    initialPriority: String,
    initialCategory: String,
    initialRecurrence: String,
    onDismiss: () -> Unit,
    onConfirm: (desc: String, priority: String, category: String, recurrence: String) -> Unit
) {
    var description by remember { mutableStateOf(initialDescription) }
    var priority by remember { mutableStateOf(initialPriority) }
    var category by remember { mutableStateOf(initialCategory) }
    var recurrence by remember { mutableStateOf(initialRecurrence) }

    val priorities = listOf("ALTA", "MEDIA", "BAJA")
    val categories = listOf("General", "Trabajo", "Personal", "Estudio")
    val recurrences = listOf("NINGUNA", "DIARIA", "SEMANAL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción de la tarea") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 7. Prioridad
                Text("Prioridad:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    priorities.forEach { prio ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(prio, fontSize = 11.sp) }
                        )
                    }
                }

                // 9. Categoría
                Text("Categoría:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                // 8. Recurrencia
                Text("Repetir:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    recurrences.forEach { rec ->
                        FilterChip(
                            selected = recurrence == rec,
                            onClick = { recurrence = rec },
                            label = { Text(rec, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(description, priority, category, recurrence) },
                enabled = description.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}