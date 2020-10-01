import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.analysis.KotlinAnalysis
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.ExtensionPoint
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import java.io.File
import kotlin.reflect.KClass

class FindUsagesVisitor : DeclarationDescriptorVisitorEmptyBodies<List<String>, KClass<*>>() {
    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor?, data: KClass<*>?): List<String> {
        return descriptor?.
            getMemberScope()?.classes()?.flatMap { visitClassDescriptor(it, data) }.orEmpty() +
                descriptor?.getMemberScope()?.functions()?.flatMap { visitFunctionDescriptor(it, data) }.orEmpty()
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor?, data: KClass<*>?): List<String> {
        val name = descriptor?.name?.asString().orEmpty()
        return descriptor?.constructors?.flatMap { visitConstructorDescriptor(it, data).prefixedWith(name) }.orEmpty() +
                descriptor?.unsubstitutedMemberScope?.functions()?.flatMap { visitFunctionDescriptor(it, data).prefixedWith(name) }.orEmpty() +
                descriptor?.unsubstitutedMemberScope?.classes()?.flatMap { visitClassDescriptor(it, data).prefixedWith(name) }.orEmpty()
    }

    override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor?, data: KClass<*>?): List<String> =
        visitCallable(constructorDescriptor, data)

    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor?, data: KClass<*>?): List<String> {
        return listOfNotNull(descriptor?.type?.fqName?.asString())
    }

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor?, data: KClass<*>?): List<String> =
        visitCallable(descriptor, data)

    private fun visitCallable(descriptor: CallableMemberDescriptor?, data: KClass<*>?): List<String> {
        val hasDesiredParameter = descriptor?.valueParameters?.flatMap { visitValueParameterDescriptor(it, data) }
            ?.any { it == data?.qualifiedName }

        return if(hasDesiredParameter == true){
            listOfNotNull(descriptor.name.asString())
        } else {
            emptyList()
        }
    }

    private fun MemberScope.classes(): List<ClassDescriptor> =
        getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { true }.
            filterIsInstance<ClassDescriptor>()

    private fun MemberScope.functions(): List<FunctionDescriptor> =
        getContributedDescriptors(DescriptorKindFilter.FUNCTIONS) { true }.
            filterIsInstance<FunctionDescriptor>()

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
    val kotlinAnalysis = KotlinAnalysis(ctx)
    val (environment, facade) = kotlinAnalysis[sourceset]
    val packageFragments = environment.getSourceFiles().asSequence()
        .map { it.packageFqName }
        .distinct()
        .mapNotNull { facade.resolveSession.getPackageFragment(it) }
        .toList()
    val visitor = FindUsagesVisitor()
    val searchFor = KClass::class
    val result: List<String> = visitor.run {
        packageFragments.mapNotNull { it as? PackageFragmentDescriptor }.flatMap {
            visitPackageFragmentDescriptor(
                it,
                searchFor
            )
        }
    }
    println("Usages as a parameter for: ${searchFor.qualifiedName}: ${result.joinToString { it }}")
}

fun List<String>.prefixedWith(prefix: String): List<String> = map { "$prefix/$it" }