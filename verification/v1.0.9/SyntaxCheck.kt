import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import java.io.File
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val env = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(env.project, false)
        var count = 0
        var errors = 0
        File(args[0]).walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            count++
            val text = f.readText()
            val psi = factory.createFile(f.name, text)
            PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java).forEach { e ->
                errors++
                val line = text.take(e.textOffset).count { it == '\n' } + 1
                println("${f.relativeTo(File(args[0]))}:$line: ${e.errorDescription}")
            }
        }
        println("Kotlin files parsed: $count; syntax errors: $errors")
        if (errors > 0) throw IllegalStateException("Kotlin syntax errors")
    } finally { Disposer.dispose(disposable) }
}
