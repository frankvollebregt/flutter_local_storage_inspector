package com.chimerapps.storageinspector.ui.ide.view.key_value

import com.chimerapps.storageinspector.api.protocol.model.StorageType
import com.chimerapps.storageinspector.api.protocol.model.ValueWithType
import com.chimerapps.storageinspector.api.protocol.model.key_value.KeyValueServerValue
import com.chimerapps.storageinspector.ui.ide.settings.KeyValueTableConfiguration
import com.chimerapps.storageinspector.ui.ide.settings.StorageInspectorProjectSettings
import com.chimerapps.storageinspector.ui.util.list.DiffUtilDispatchModel
import com.chimerapps.storageinspector.ui.util.list.TableModelDiffUtilDispatchModel
import com.chimerapps.storageinspector.ui.util.localization.Tr
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ComboBoxCellEditor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableCellEditor
import javax.swing.table.TableColumnModel


/**
 * @author Nicola Verbeeck
 */
class KeyValueTableView(
    private val project: Project,
    private val removeKeys: (List<ValueWithType>) -> Unit,
    private val editValue: (key: ValueWithType, newValue: ValueWithType) -> Unit,
) : TableView<KeyValueServerValue>() {

    private val internalModel: ListTableModel<KeyValueServerValue>

    val dispatchModel: DiffUtilDispatchModel<KeyValueServerValue>
    private var isResizingColumns: Boolean = false
    private val columnObserver = object : TableColumnModelListener {
        override fun columnAdded(e: TableColumnModelEvent?) {}

        override fun columnRemoved(e: TableColumnModelEvent?) {}

        override fun columnMoved(e: TableColumnModelEvent?) {}

        override fun columnMarginChanged(e: ChangeEvent) {
            isResizingColumns = true
        }

        override fun columnSelectionChanged(e: ListSelectionEvent?) {}
    }

    init {
        tableHeader.reorderingAllowed = false
        rowHeight = PlatformIcons.CLASS_ICON.iconHeight * 2
        preferredScrollableViewportSize = JBUI.size(-1, 150)

        tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent?) {
                super.mouseReleased(e)
                commitResize()
            }
        })

        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                super.keyPressed(e)
                if (e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE) {
                    doRemoveSelectedRows()
                }
            }
        })

        internalModel = ListTableModel(
            arrayOf(
                TableViewColumnInfo(Tr.KeyValueKey.tr(), KeyValueServerValue::key, editable = false),
                TableViewColumnInfo(Tr.KeyValueValue.tr(), KeyValueServerValue::value, editable = true, onEdited = ::onValueEdited),
            ),
            listOf(),
            0
        )
        model = internalModel
        columnModel.addColumnModelListener(columnObserver)

        StorageInspectorProjectSettings.instance(project).state.configuration?.keyValueTableConfiguration?.let { configuration ->
            if (configuration.keyWidth >= 0) {
                setColumnPreferredSize(0, configuration.keyWidth)
            }
            if (configuration.valueWidth >= 0) {
                setColumnPreferredSize(1, configuration.valueWidth)
            }
        }

        dispatchModel = TableModelDiffUtilDispatchModel(internalModel)
    }


    private fun setColumnPreferredSize(index: Int, width: Int) {
        val column = columnModel.getColumn(index)
        column.minWidth = 15
        column.maxWidth = Integer.MAX_VALUE
        column.preferredWidth = width
    }

    private fun onValueEdited(keyValueServerValue: KeyValueServerValue, newValue: String) {
        val converted = when (keyValueServerValue.value.type) {
            StorageType.string -> newValue
            StorageType.int -> newValue.toLong()
            StorageType.double -> newValue.toDouble()
            StorageType.bool -> (newValue.lowercase() == "true" || newValue.lowercase() == Tr.TypeBooleanTrue.tr().lowercase())
            StorageType.datetime -> TODO()
            StorageType.binary -> TODO()
            StorageType.stringlist -> TODO()
        }
        editValue(keyValueServerValue.key, keyValueServerValue.value.copy(value = converted))
    }

    fun doRemoveSelectedRows() {
        removeKeys(selectedRows.map { index -> internalModel.getItem(index).key })
    }

    private fun commitResize() {
        if (!isResizingColumns) return
        isResizingColumns = false

        StorageInspectorProjectSettings.instance(project).updateState {
            copy(
                configuration = updateConfiguration {
                    copy(
                        keyValueTableConfiguration = KeyValueTableConfiguration(
                            keyWidth = columnModel.getColumn(0).width,
                            valueWidth = columnModel.getColumn(1).width,
                        ),
                    )
                },
            )
        }
    }

    override fun setColumnModel(columnModel: TableColumnModel) {
        this.columnModel?.removeColumnModelListener(columnObserver)
        super.setColumnModel(columnModel)
        columnModel.addColumnModelListener(columnObserver)
    }
}

private class TableViewColumnInfo(
    name: String,
    private val selector: (KeyValueServerValue) -> ValueWithType,
    private val editable: Boolean,
    private val onEdited: (KeyValueServerValue, stringValue: String) -> Unit = { _, _ -> },
) : ColumnInfo<KeyValueServerValue, String>(name) {

    override fun valueOf(item: KeyValueServerValue): String {
        return selector(item).asString
    }

    override fun isCellEditable(item: KeyValueServerValue?): Boolean = editable

    override fun getEditor(item: KeyValueServerValue): TableCellEditor? {
        return when (selector(item).type) {
            StorageType.bool -> return object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): List<String> {
                    return listOf(Tr.TypeBooleanTrue.tr(), Tr.TypeBooleanFalse.tr())
                }
            }
            else -> null
        }
    }

    override fun setValue(item: KeyValueServerValue, value: String) {
        onEdited(item, value)
    }
}