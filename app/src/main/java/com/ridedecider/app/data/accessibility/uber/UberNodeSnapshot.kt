package com.ridedecider.app.data.accessibility.uber

/**
 * Representación inmutable y desacoplada en memoria de un nodo del árbol de accesibilidad.
 *
 * Aísla los datos del ciclo de vida y reciclado de [android.view.accessibility.AccessibilityNodeInfo],
 * evitando fugas de memoria y permitiendo que los parsers analicen la interfaz sobre una estructura
 * pura, segura y 100% testeable en la JVM.
 */
data class UberNodeSnapshot(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val boundsInScreen: String? = null,
    val children: List<UberNodeSnapshot> = emptyList()
) {

    /**
     * Devuelve una lista plana con todos los nodos del árbol (el nodo actual y todos sus descendientes)
     * mediante recorrido en profundidad (DFS).
     */
    fun flatten(): List<UberNodeSnapshot> {
        val result = mutableListOf<UberNodeSnapshot>()
        fun traverse(node: UberNodeSnapshot) {
            result.add(node)
            for (child in node.children) {
                traverse(child)
            }
        }
        traverse(this)
        return result
    }

    /**
     * Busca todos los nodos en el subárbol cuyo [text] o [contentDescription] contenga el texto buscado.
     *
     * @param searchText Texto a buscar.
     * @param ignoreCase Si es true, la búsqueda no distingue entre mayúsculas y minúsculas.
     * @return Lista de nodos que coinciden con el criterio.
     */
    fun findNodesByText(searchText: String, ignoreCase: Boolean = true): List<UberNodeSnapshot> {
        if (searchText.isBlank()) return emptyList()
        return flatten().filter { node ->
            (node.text?.contains(searchText, ignoreCase = ignoreCase) == true) ||
                    (node.contentDescription?.contains(searchText, ignoreCase = ignoreCase) == true)
        }
    }

    /**
     * Busca todos los nodos en el subárbol que tengan el identificador de recurso especificado.
     *
     * @param viewId Identificador completo de la vista (ej. "com.ubercab.driver:id/accept_button").
     * @return Lista de nodos con el viewId correspondiente.
     */
    fun findNodesByViewId(viewId: String): List<UberNodeSnapshot> {
        if (viewId.isBlank()) return emptyList()
        return flatten().filter { it.viewIdResourceName == viewId }
    }
}
