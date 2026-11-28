package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/** 两级分类选择器状态（架构 §5.2 M5）：只回传 categoryId，不修改分类结构。 */
data class CategoryPickerState(
    val categories: List<TimeCategory> = emptyList(),
    val recentCategoryIds: List<TimeCategoryId> = emptyList(),
    val selectedId: TimeCategoryId? = null,
    val emptyMessage: String? = null,
)

/**
 * “全部分类”两级选择器（05 §3.3.1 B）。
 *
 * - 最近使用最多 5 项；全部分类区只渲染两级，第三级永不渲染；
 * - 父级行分两个目标：点名称选中父级本身，行尾展开/收起子级；
 * - 空态提示到设置添加，不提供行内新建；选中后关闭并回填调用方。
 */
@Composable
fun CategoryPicker(
    state: CategoryPickerState,
    onSelect: (TimeCategoryId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 默认展开全部顶级，保证“两级完整展示”；子级行不提供添加入口（禁止第三级）。
    var expandedParentIds by remember(state.categories) {
        mutableStateOf(
            state.categories.filter { it.parentId == null }.map { it.id }.toSet(),
        )
    }

    Column(modifier = modifier.fillMaxWidth().testTag("category_picker")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选择分类",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("category_picker_back"),
            ) {
                Text("返回")
            }
        }

        if (state.categories.isEmpty()) {
            NautileEmptyState(
                title = "还没有分类",
                description = state.emptyMessage ?: "请到设置 > 分类管理添加",
                iconRes = R.drawable.ic_category,
                modifier = Modifier.testTag("category_picker_empty"),
            )
            return
        }

        val byId = state.categories.associateBy { it.id }
        val recent = state.recentCategoryIds.take(RECENT_LIMIT).mapNotNull { byId[it] }
        if (recent.isNotEmpty()) {
            SectionLabel("最近使用")
            recent.forEach { category ->
                PickerRow(
                    label = categoryPath(state.categories, category.id),
                    selected = category.id == state.selectedId,
                    testTag = "category_recent_${category.id.value}",
                    onClick = { onSelect(category.id) },
                )
            }
        }

        SectionLabel("全部分类")
        val topLevel = state.categories.filter { it.parentId == null }
        val childrenByParent = state.categories.filter { it.parentId != null }.groupBy { it.parentId }
        topLevel.forEach { parent ->
            val children = childrenByParent[parent.id].orEmpty()
            val expanded = parent.id in expandedParentIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(parent.id) }
                        .padding(start = NautileSpacing.space2, top = NautileSpacing.space1, bottom = NautileSpacing.space1)
                        .testTag("category_parent_${parent.id.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = parent.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (parent.id == state.selectedId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (children.isNotEmpty()) {
                    NautileIconButton(
                        iconRes = R.drawable.ic_expand_more,
                        contentDescription = if (expanded) "收起子分类" else "展开子分类",
                        onClick = {
                            expandedParentIds = if (expanded) {
                                expandedParentIds - parent.id
                            } else {
                                expandedParentIds + parent.id
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (expanded) 180f else 0f
                        },
                        testTag = "category_expand_${parent.id.value}",
                    )
                }
            }
            if (expanded) {
                children.forEach { child ->
                    PickerRow(
                        label = child.name,
                        selected = child.id == state.selectedId,
                        indent = true,
                        testTag = "category_child_${child.id.value}",
                        onClick = { onSelect(child.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = NautileSpacing.space2,
            top = NautileSpacing.space2,
            bottom = NautileSpacing.space1,
        ),
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    indent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) NautileSpacing.space6 else NautileSpacing.space2,
                top = NautileSpacing.space1,
                bottom = NautileSpacing.space1,
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (indent) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private const val RECENT_LIMIT = 5
