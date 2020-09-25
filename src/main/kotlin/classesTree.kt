import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.analysis.KotlinAnalysis
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
sealed class GraphNode {
    abstract val name: String
}

@Serializable
data class Leaf(override val name: String) : GraphNode()

@Serializable
data class Node(override val name: String, val children: List<GraphNode>) : GraphNode()

fun main(args: Array<String>) {
    val workingDir = createTempDir()
    "git clone https://github.com/cbeust/klaxon.git".runCommand(workingDir)
    val sourceset = DokkaSourceSetImpl(
        displayName = "klaxon",
        sourceSetID = DokkaSourceSetID("klaxonScopeID", "klaxonJVM"),
        sourceRoots = setOf(File("${workingDir.absolutePath}/klaxon/klaxon/src/main/kotlin"))
    )
    val koltinAnalysis: KotlinAnalysis = KotlinAnalysis(listOf(sourceset), DokkaConsoleLogger)
    val (environment, facade) = koltinAnalysis[sourceset]
    val packageFragments = environment.getSourceFiles().asSequence()
        .map { it.packageFqName }
        .distinct()
        .mapNotNull { facade.resolveSession.getPackageFragment(it) }
        .toList()

    val classes = packageFragments.mapNotNull { it as? PackageFragmentDescriptor }.flatMap { it.classes }
    val graph = classes.map { descriptor -> constructClassesGraph(descriptor.name.asString(), descriptor.supertypes) }
//    println(Json.encodeToString(graph))

    val supertypesGraph = graph.map { constructSubtypesGraph("kotlin.Any", it) }
    println(Json.encodeToString(supertypesGraph))
}

fun constructSubtypesGraph(start: String, supertypesGraph: GraphNode): GraphNode {
    val supertypes = subtypesOf(start, supertypesGraph).distinctBy { it.name }
    return if(supertypes.isEmpty()){
        Leaf(start)
    } else {
        Node(start, supertypes.map { constructSubtypesGraph(it.name, supertypesGraph) })
    }
}

fun constructClassesGraph(name: String, superTypes: Collection<KotlinType>): GraphNode =
    if (superTypes.isEmpty()) Leaf(name)
    else Node(name, superTypes.map { constructClassesGraph(it.name, it.supertypes()) })

fun GraphNode.searchRecursivelyInChildren(
    predicate: (GraphNode) -> Boolean,
    acc: List<GraphNode> = emptyList()
): List<GraphNode> =
    when {
        predicate(this) && this is Leaf -> acc + this
        predicate(this) -> (this as Node).children.flatMap { it.searchRecursivelyInChildren(predicate, acc + this) }
        this is Node -> children.flatMap { it.searchRecursivelyInChildren(predicate, acc) }
        else -> acc
    }

fun subtypesOf(name: String, graph: GraphNode): List<GraphNode> =
    graph.searchRecursivelyInChildren({ node -> node is Node && node.children.any { it.name == name } })

val ClassDescriptor.supertypes: Collection<KotlinType>
    get() = typeConstructor.supertypes

val KotlinType.name: String
    get() = fqName?.asString()!!

val PackageFragmentDescriptor.classes: List<ClassDescriptor>
    get() = getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { true }
        .filterIsInstance<ClassDescriptor>()