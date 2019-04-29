import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import org.jetbrains.annotations.Nullable
import org.openrndr.cascading.annotations.Cascading
import java.io.File
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy


import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@AutoService(Processor::class)
class CascadingProcessor : AbstractProcessor() {

    private val generatedSourcesRoot by lazy { processingEnv.options["kapt.kotlin.generated"].orEmpty() }


    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        if (generatedSourcesRoot.isEmpty()) {
            printError("Can't find the target directory for generated Kotlin files.")
            return false
        }
        if (roundEnv != null) {

            val optionals = roundEnv.getElementsAnnotatedWith(Nullable::class.java).filter {
                it.kind == ElementKind.FIELD
            }.toSet()

            roundEnv.getElementsAnnotatedWith(Cascading::class.java).forEach {
                makeClass(optionals, it)
            }
        }

        return false
    }

    private fun makeClass(optionals: Set<Element>, fieldElement: Element) {
        val packageOfMethod = processingEnv.elementUtils.getPackageOf(fieldElement).toString()
        val annotatedValue = fieldElement.getAnnotation(Cascading::class.java)

        val classType = fieldElement.asType().asTypeName() as ClassName
        val className = fieldElement.simpleName.toString()
        val inClassName = fieldElement.simpleName.toString() + "In"
        val outClassName = fieldElement.simpleName.toString() + "Out"
        val inBuilder = TypeSpec.classBuilder(inClassName)
        val outBuilder = TypeSpec.classBuilder(outClassName)

        val cascadableFields = fieldElement.enclosedElements.filter { it.kind == ElementKind.FIELD }

        val outConstructorBuilder = FunSpec.constructorBuilder()
        fieldElement.enclosedElements.filter { it.kind == ElementKind.FIELD }.forEach {
            val inTypeName = it.asType().asTypeName().copy(nullable = true).kotlinType().replaceInType(classType as ClassName)
            val inPropSpec = PropertySpec.builder(it.simpleName.toString(), inTypeName).mutable(true).initializer("null").build()
            inBuilder.addProperty(inPropSpec)
        }

        outConstructorBuilder.addParameter("in${className}", ClassName(packageOfMethod,inClassName))
        fieldElement.enclosedElements.filter { it.kind == ElementKind.FIELD }.forEach {
            val outTypeName = it.asType().asTypeName().kotlinType().replaceInType(classType).let { f -> f.copy(nullable = it in optionals || f.isNullable) }
            outConstructorBuilder.addParameter(it.simpleName.toString(), outTypeName)
        }

        outBuilder.primaryConstructor(outConstructorBuilder.build())

        run {

            val outTypeName = ClassName(packageOfMethod,inClassName)
            val outPropSpec = PropertySpec.builder("in${className}", outTypeName).initializer("in${className}").build()
            outBuilder.addProperty(outPropSpec)
        }
        fieldElement.enclosedElements.filter { it.kind == ElementKind.FIELD }.forEach {
            val outTypeName = it.asType().asTypeName().kotlinType().replaceInType(classType).let { f -> f.copy(nullable = it in optionals || f.isNullable) }
            val outPropSpec = PropertySpec.builder(it.simpleName.toString(), outTypeName).initializer(it.simpleName.toString()).build()
            outBuilder.addProperty(outPropSpec)
        }

        val cascade = FunSpec.builder("cascade")
                .addParameter("onto", ClassName(packageOfMethod, inClassName))
                .returns(ClassName(packageOfMethod, inClassName))
                .addCode(CodeBlock.builder().apply {
                    addStatement("val result = ${inClassName}()")
                    cascadableFields.forEach {
                        addStatement("result.${it.simpleName} = onto.${it.simpleName} ?: ${it.simpleName}")
                    }
                    addStatement("return result")
                }.build()).build()
        inBuilder.addFunction(cascade)

        val resolve = FunSpec.builder("resolve").addParameter("defaults", ClassName(packageOfMethod, className))
                .returns(ClassName(packageOfMethod, outClassName)).addCode(
                        CodeBlock.builder().apply {
                            val arguments = cascadableFields.map {

                                if (it in optionals) {

                                    it.simpleName.toString()
                                } else {
                                    it.simpleName.toString() + "?: defaults.${it.simpleName}"
                                }
                            }.joinToString(",")
                            addStatement("return ${outClassName}(this, ${arguments})")

                        }.build()
                )

        inBuilder.addFunction(resolve.build())

        val file = File(generatedSourcesRoot)
        file.mkdir()
        FileSpec.builder(packageOfMethod, "GeneratedClass"+classType.simpleName)
                .addType(outBuilder.build())
                .addType(inBuilder.build()).build().writeTo(file)
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(Cascading::class.java.canonicalName)
    }

    private fun printError(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message)
    }

    private fun printWarning(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, message)
    }

    fun TypeName.replaceInType(toReplace: ClassName, nullable:Boolean=true): TypeName {
        val replaceName = toReplace.simpleName
        if (this is ParameterizedTypeName) {
            return if (this.typeArguments.any { (it as ClassName).simpleName.replace("?", "") == replaceName }) {
                rawType.parameterizedBy(*(this.typeArguments.map { it.replaceInType(toReplace,false) }.toTypedArray())).copy(nullable = true)
            } else {
                this
            }
        } else if (this is ClassName) {
            if (this.simpleName == toReplace.simpleName) {
                return ClassName(toReplace.packageName, toReplace.simpleName + "In").copy(nullable=nullable)
            }
        }
        return this
    }

    fun TypeName.kotlinType(): TypeName {
        val toString = this.toString()
        return when {
            toString == "java.lang.String" -> ClassName("kotlin", "String")
            toString == "java.lang.String?" -> ClassName("kotlin", "String").copy(nullable = true)
            toString == "kotlin.Array<java.lang.Integer>" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Int"))
            toString == "kotlin.Array<java.lang.Integer>?" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Int")).copy(nullable = true)
            toString == "kotlin.Array<java.lang.Float>" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Float"))
            toString == "kotlin.Array<java.lang.Float>?" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Float")).copy(nullable = true)
            toString == "kotlin.Array<java.lang.Double>" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Double"))
            toString == "kotlin.Array<java.lang.Double>?" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "Double")).copy(nullable = true)
            toString == "kotlin.Array<java.lang.String>" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "String"))
            toString == "kotlin.Array<java.lang.String>?" -> ClassName("kotlin", "Array").parameterizedBy(ClassName("kotlin", "String")).copy(nullable = true)
            else -> {
                this
            }
        }
    }
}
