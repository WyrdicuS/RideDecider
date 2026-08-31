package com.ridedecider.app.data.accessibility.uber

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Conversor responsable de transformar un árbol activo de [AccessibilityNodeInfo] de Android
 * en un árbol inmutable y seguro de [UberNodeSnapshot].
 *
 * Realiza la copia profunda inmediata de atributos y cadenas de texto, desacoplando la memoria
 * de los nodos del framework sin invocar [AccessibilityNodeInfo.recycle] para evitar corrupciones
 * en versiones modernas de Android (API 34+).
 */
class UberNodeSnapshotConverter {

    /**
     * Convierte recursivamente un [AccessibilityNodeInfo] en un [UberNodeSnapshot].
     *
     * @param rootNode Nodo raíz del árbol de accesibilidad de la ventana activa de Uber.
     * @param maxDepth Límite de profundidad de recursión para evitar desbordamientos de pila.
     * @return [UberNodeSnapshot] inmutable o `null` si el nodo raíz es nulo.
     */
    fun createSnapshot(rootNode: AccessibilityNodeInfo?, maxDepth: Int = 45): UberNodeSnapshot? {
        if (rootNode == null) return null
        return buildSnapshot(rootNode, currentDepth = 0, maxDepth = maxDepth)
    }

    private fun buildSnapshot(
        node: AccessibilityNodeInfo,
        currentDepth: Int,
        maxDepth: Int
    ): UberNodeSnapshot {
        val rawText = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()
        val stateDesc = try { node.stateDescription?.toString() } catch (_: Exception) { null }
        val hint = try { node.hintText?.toString() } catch (_: Exception) { null }
        val tooltip = try { node.tooltipText?.toString() } catch (_: Exception) { null }
        val paneTitle = try { node.paneTitle?.toString() } catch (_: Exception) { null }
        val actionLabel = try {
            node.actionList?.firstOrNull { !it.label.isNullOrBlank() }?.label?.toString()
        } catch (_: Exception) { null }

        val text = when {
            !rawText.isNullOrBlank() -> rawText
            !stateDesc.isNullOrBlank() -> stateDesc
            !hint.isNullOrBlank() -> hint
            !actionLabel.isNullOrBlank() -> actionLabel
            !tooltip.isNullOrBlank() -> tooltip
            !paneTitle.isNullOrBlank() -> paneTitle
            else -> null
        }
        val viewId = node.viewIdResourceName
        val className = node.className?.toString()
        val packageName = node.packageName?.toString()
        val isClickable = node.isClickable
        val isEnabled = node.isEnabled
        val isVisibleToUser = node.isVisibleToUser

        val boundsStr = try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"
        } catch (_: Exception) {
            null
        }

        val childrenList = mutableListOf<UberNodeSnapshot>()

        if (currentDepth < maxDepth) {
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val childNode = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }

                if (childNode != null) {
                    val childSnapshot = buildSnapshot(childNode, currentDepth + 1, maxDepth)
                    childrenList.add(childSnapshot)
                }
            }
        }

        return UberNodeSnapshot(
            text = text,
            contentDescription = contentDescription,
            viewIdResourceName = viewId,
            className = className,
            packageName = packageName,
            isClickable = isClickable,
            isEnabled = isEnabled,
            isVisibleToUser = isVisibleToUser,
            boundsInScreen = boundsStr,
            children = childrenList
        )
    }
}
