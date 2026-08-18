import zipfile, os, struct

jarPath = r"C:\Users\trasa\.gradle\caches\modules-2\files-2.1\maven.modrinth\creating-space\1.7.18\b979ff298fdf6a61dabeb9325d1adda7b4db8da8\creating-space-1.7.18.jar"

with zipfile.ZipFile(jarPath, 'r') as z:
    print("=== ALL FILES in JAR ===")
    for info in z.infolist():
        print(info.filename)
