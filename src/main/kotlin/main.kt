import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.analysis.*
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.io.File

class Visitor : DeclarationDescriptorVisitorEmptyBodies<String, Unit>() {
    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor?, data: Unit?): String {
        val classes = descriptor?.
            getMemberScope()?.
            getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { true }?.
            filterIsInstance<ClassDescriptor>()?.map { visitClassDescriptor(it, data) }.orEmpty()
        return classes.joinToString { it }
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor?, data: Unit?): String {
        return descriptor?.name?.asString() ?: ""
    }
}

fun main(args: Array<String>) {
    val sourceset = DokkaSourceSetImpl(
            displayName = "sample",
            sourceSetID = DokkaSourceSetID("scopeId", "sourceSetName"),
            sourceRoots = setOf(File("/Users/marcinaman/Documents/AnalyseKotlin/src/main/kotlin"))
    )
    val dokkaConfiguration: DokkaConfiguration = DokkaConfigurationImpl(
            sourceSets = listOf(sourceset)
    )
    val dokkaLogger: DokkaLogger = DokkaConsoleLogger
    val koltinAnalysis: KotlinAnalysis = KotlinAnalysis(dokkaConfiguration, dokkaLogger)
    val (environment, facade) = koltinAnalysis[sourceset]
    val packageFragments = environment.getSourceFiles().asSequence()
            .map { it.packageFqName }
            .distinct()
            .mapNotNull { facade.resolveSession.getPackageFragment(it) }
            .toList()
    val visitor = Visitor()
    val result = visitor.run {
        packageFragments.mapNotNull { it as? PackageFragmentDescriptor }.map {
            visitPackageFragmentDescriptor(
                    it,
                    Unit
            )
        }
    }
    println("result: ${result}")
}