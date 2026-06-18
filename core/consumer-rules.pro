# Feature entry-point interfaces are implemented in dynamic feature modules and instantiated
# reflectively by FeatureLoader, so they (and their implementors) must survive shrinking.
-keep interface com.hereliesaz.logkitty.core.feature.** { *; }
