package com.github.stephanenicolas.ormgap

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.TaskProvider

/**
 * ORM LITE ANDROID PLUGIN
 * It will:
 * <ul>
 *   <li> add a task to create the configuration file for ORM LITE
 *   <li> insert it into the build task graph of your android project
 *   <li> let you configure the name of the configuration file ?
 *   <li> work for libraries ?
 *   <li> work for tests ?
 * </ul>*/
public class ORMGAPPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    def hasApp = project.plugins.withType(AppPlugin)
    def hasLib = project.plugins.withType(LibraryPlugin)
    ensureProjectIsAndroidAppOrLib(hasApp, hasLib)

    def extension = getExtension()
    def pluginExtension = getPluginExtension()
    if (extension && pluginExtension) {
      project.extensions.create(extension, pluginExtension)
    }

    final def log = project.logger
    final String LOG_TAG = "ORMGAP"

    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }

    configure(project)

    variants.all { variant ->
      log.debug("In variant '${variant.name}'.")

      String variantName
      if (variant instanceof ApplicationVariant){
        variantName = ((ApplicationVariant) variant).productFlavors[0]?.name
      } else if (variant instanceof LibraryVariant){
        variantName = ((LibraryVariant) variant).mergedFlavor.name
      }

      // Use default buildvariant
      if(variantName == null || "".equals(variantName)){
          variantName = "main"
      }

      def createConfigFileTaskName = "createORMLiteConfigFile${variant.name.capitalize()}"
      def createConfigFileTask = project.tasks.register(createConfigFileTaskName, CreateOrmLiteConfigTask) {
        description = "Create an ORM Lite configuration file"


        TaskProvider<JavaCompile> javaCompile = variant.getJavaCompileProvider()

        FileCollection classpathFileCollection = project.files(project.android.bootClasspath)
        classpathFileCollection += javaCompile.get().classpath
        classpathFileCollection += project.files(javaCompile.get().destinationDir)

        def path = project.android.sourceSets[variantName].java.srcDirs[0].canonicalPath
        if (new File(path).exists()) {
          setSources(path)
        } else {
          setSources(project.android.sourceSets["main"].java.srcDirs[0].canonicalPath)
        }

        path = project.android.sourceSets[variantName].assets.srcDirs[0].canonicalPath
        setDestDirFolder(path)
        setClasspath(classpathFileCollection)
        into(project.ormgap.configFileName)
      }
      createConfigFileTask.configure { dependsOn javaCompile }

      variant.mergeAssetsProvider.configure { dependsOn createConfigFileTask }

      log.debug("ORMLite config file creation task installed after mergeAssets task.")
      if (!hasLib) {
        variant.installProvider?.configure { dependsOn createConfigFileTask }
      }
      log.debug("Done with variant '${variant.name}'.")
    }
    log.debug("Done.")
  }

  protected void ensureProjectIsAndroidAppOrLib(PluginCollection<AppPlugin> hasApp,
      PluginCollection<LibraryPlugin> hasLib) {
    if (!hasApp && !hasLib) {
      throw new IllegalStateException("'android' or 'android-library' plugin required.")
    }
  }

  /**
   * Hook to configure the project under build.
   * Can be used to add other extensions, plugins, etc.
   * @param project the project under build.
   */
  protected void configure(Project project) {
    //we use the file build.properties that contains the version of
    //the extension to use. This avoids all problems related to using version x.y.+
    Properties properties = new Properties()
    properties.load(getClass().getClassLoader().getResourceAsStream("build.properties"))
    project.dependencies {
      compileOnly 'com.github.stephanenicolas.ormgap:ormgap-ormlite-extension:' + properties.get("com.github.stephanenicolas.ormgap.version")
    }
  }

  /**
   * @return the name of the class of the plugin extension associated to the project's extension.
   * Can be null, then no extension is created.
   * @see #getExtension()
   */
  private Class getPluginExtension() {
    ORMGAPPluginExtension
  }

  /**
   * @return the extension of the project that this plugin can create.
   * It will be associated to the plugin extension.
   * Can be null, then no extension is created.
   * @see #getPluginExtension()
   */
  private String getExtension() {
    "ormgap"
  }
}
