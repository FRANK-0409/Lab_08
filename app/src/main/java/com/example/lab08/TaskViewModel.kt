package com.example.lab08

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class TaskFilter {
    ALL, PENDING, COMPLETED
}

enum class SortOrder {
    DATE_DESC, NAME_ASC, PRIORITY_HIGH
}

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    private val _rawTasks = MutableStateFlow<List<Task>>(emptyList())

    // 4. Buscar
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // 3. Filtrar por Estado
    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    // 9. Filtrar por Categoría
    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory

    // 5. Ordenar
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    // 10. Estado de Sincronización
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    // Lista combinada con filtros, búsqueda y ordenamiento
    val tasks: StateFlow<List<Task>> = combine(
        _rawTasks,
        _searchQuery,
        _filter,
        _selectedCategory,
        _sortOrder
    ) { tasks, query, filter, category, sort ->
        tasks.filter { task ->
            val matchesQuery = task.description.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.PENDING -> !task.isCompleted
                TaskFilter.COMPLETED -> task.isCompleted
            }
            val matchesCategory = if (category == "Todas") true else task.category.equals(category, ignoreCase = true)
            
            matchesQuery && matchesFilter && matchesCategory
        }.sortedWith { t1, t2 ->
            when (sort) {
                SortOrder.DATE_DESC -> t2.id.compareTo(t1.id)
                SortOrder.NAME_ASC -> t1.description.compareTo(t2.description, ignoreCase = true)
                SortOrder.PRIORITY_HIGH -> priorityWeight(t2.priority).compareTo(priorityWeight(t1.priority))
            }
        }
    }.toStateFlow(viewModelScope, emptyList())

    init {
        loadTasks()
    }

    private fun priorityWeight(priority: String): Int {
        return when (priority.uppercase()) {
            "ALTA" -> 3
            "MEDIA" -> 2
            "BAJA" -> 1
            else -> 0
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _rawTasks.value = dao.getAllTasks()
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilter(filter: TaskFilter) { _filter.value = filter }
    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSortOrder(sortOrder: SortOrder) { _sortOrder.value = sortOrder }

    // Agregar Tarea con Prioridad (7), Categoría (9) y Recurrencia (8)
    fun addTask(
        description: String,
        priority: String = "MEDIA",
        category: String = "General",
        recurrence: String = "NINGUNA"
    ) {
        if (description.isBlank()) return
        val newTask = Task(
            description = description.trim(),
            priority = priority,
            category = category,
            recurrence = recurrence,
            isSynced = false
        )
        viewModelScope.launch {
            dao.insertTask(newTask)
            loadTasks()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted, isSynced = false)
            dao.updateTask(updatedTask)

            // 8. Tareas Recurrentes: si se completa una tarea recurrente, creamos la siguiente instancia
            if (!task.isCompleted && task.recurrence != "NINGUNA") {
                val nextTask = Task(
                    description = "${task.description} (${task.recurrence.lowercase()})",
                    priority = task.priority,
                    category = task.category,
                    recurrence = task.recurrence,
                    isSynced = false
                )
                dao.insertTask(nextTask)
            }
            loadTasks()
        }
    }

    // 1. Editar Tarea
    fun editTask(
        task: Task,
        newDescription: String,
        newPriority: String,
        newCategory: String,
        newRecurrence: String
    ) {
        if (newDescription.isBlank()) return
        viewModelScope.launch {
            val updatedTask = task.copy(
                description = newDescription.trim(),
                priority = newPriority,
                category = newCategory,
                recurrence = newRecurrence,
                isSynced = false
            )
            dao.updateTask(updatedTask)
            loadTasks()
        }
    }

    // 2. Eliminar Tarea Individual
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            loadTasks()
        }
    }

    fun deleteAllTasks() {
        viewModelScope.launch {
            dao.deleteAllTasks()
            loadTasks()
        }
    }

    // 10. Sincronización en la Nube
    fun syncWithCloud(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.delay(1200) // Simulación de petición de red
            val currentList = dao.getAllTasks()
            currentList.forEach { task ->
                if (!task.isSynced) {
                    dao.updateTask(task.copy(isSynced = true))
                }
            }
            _isSyncing.value = false
            loadTasks()
            onComplete()
        }
    }
}

private fun <T> kotlinx.coroutines.flow.Flow<T>.toStateFlow(
    scope: kotlinx.coroutines.CoroutineScope,
    initialValue: T
): StateFlow<T> {
    val state = MutableStateFlow(initialValue)
    scope.launch {
        collect { state.value = it }
    }
    return state
}