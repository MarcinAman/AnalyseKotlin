import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.analysis.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.ExtensionPoint
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class Visitor : DeclarationDescriptorVisitorEmptyBodies<List<String>, Unit>() {
    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor?, data: Unit?): List<String> {
        return descriptor?.
            getMemberScope()?.
            getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { true }?.
            filterIsInstance<ClassDescriptor>()?.flatMap { visitClassDescriptor(it, data) }.orEmpty()
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor?, data: Unit?): List<String> {
        return listOfNotNull(descriptor?.name?.asString())
    }
}

fun main(args: Array<String>) {
    val workingDir = createTempDir()
    "git clone https://github.com/cbeust/klaxon.git".runCommand(workingDir)
    val sourceset = DokkaSourceSetImpl(
            displayName = "klaxon",
            sourceSetID = DokkaSourceSetID("klaxonScopeID", "klaxonJVM"),
            sourceRoots = setOf(File("${workingDir.absolutePath}/klaxon/klaxon/src/main/kotlin"))
    )
    val ctx = object: DokkaContext {
        override fun <T : DokkaPlugin> plugin(kclass: KClass<T>): T? = TODO("Not yet implemented")
        override fun <T : Any, E : ExtensionPoint<T>> get(point: E): List<T> = TODO("Not yet implemented")
        override fun <T : Any, E : ExtensionPoint<T>> single(point: E): T = TODO("Not yet implemented")

        override val logger: DokkaLogger = DokkaConsoleLogger
        override val configuration: DokkaConfiguration = DokkaConfigurationImpl(
            sourceSets = listOf(sourceset)
        )
        override val unusedPoints: Collection<ExtensionPoint<*>> = emptyList()
    }
    val koltinAnalysis: KotlinAnalysis = KotlinAnalysis(ctx)
    val (environment, facade) = koltinAnalysis[sourceset]
    val packageFragments = environment.getSourceFiles().asSequence()
            .map { it.packageFqName }
            .distinct()
            .mapNotNull { facade.resolveSession.getPackageFragment(it) }
            .toList()
    val visitor = Visitor()
    val result: List<String> = visitor.run {
        packageFragments.mapNotNull { it as? PackageFragmentDescriptor }.flatMap {
            visitPackageFragmentDescriptor(
                    it,
                    Unit
            )
        }
    }
    println("Top 10 longest class names: ${result.map { it to it.length}.sortedByDescending { it.second }.take(10).map { "${it.first}, length: ${it.second}\n"} }}")
}

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}