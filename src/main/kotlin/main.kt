import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.analysis.KotlinAnalysis
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.io.File
import java.util.concurrent.TimeUnit

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
    val kotlinAnalysis = KotlinAnalysis(listOf(sourceset), DokkaConsoleLogger)
    val (environment, facade) = kotlinAnalysis[sourceset]
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
    println("Longest class name: ${result.map { it to it.length }.maxBy { it.second }!!.let { "${it.first}, length: ${it.second}"} }")
}

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}