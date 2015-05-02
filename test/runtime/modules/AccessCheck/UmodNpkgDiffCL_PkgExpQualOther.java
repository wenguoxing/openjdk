/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary class p1.c1 defined in the unnamed module tries to access p2.c2 defined in m2.
 *          Access is denied, since the unnamed module can read all modules but p2 in module
 *          m2 is exported specifically to module m1, not to all modules.
 * @compile p2/c2.java
 * @compile p1/c1.java
 * @build UmodNpkgDiffCL_PkgExpQualOther
 * @run main/othervm -Xbootclasspath/a:. UmodNpkgDiffCL_PkgExpQualOther
 */

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.Layer;
import java.lang.module.ModuleArtifact;
import java.lang.module.ModuleArtifactFinder;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleDescriptor.Exports;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//
// ClassLoader1 --> defines m1 --> packages m1_pinternal
// ClassLoader2 --> defines m2 --> packages p2, m2_pinternal
//
// m1 can read m2
// package p2 in m2 is not exported
//
// class p1.c1 defined in the unnamed module tries to access p2.c2 defined in m2
// Access denied, the unnamed module can read all modules but p2 in module
//             m2 is exported specifically to module m1 not to all modules.
//
public class UmodNpkgDiffCL_PkgExpQualOther {

    // Create a Layer over the boot layer.
    // Define modules within this layer to test access between
    // publically defined classes within packages of those modules.
    public void createLayerOnBoot() throws Throwable {

        // Define module:     m1
        // Can read:          module m2 and java.base
        // Packages:          m1_pinternal
        // Packages exported: none
        ModuleDescriptor descriptor_m1 =
                new ModuleDescriptor.Builder("m1")
                        .requires(md("m2"))
                        .requires(md("java.base"))
                        .build();
        Set<String> packages_m1 = Stream.of("m1_pinternal").collect(Collectors.toSet());
        ModuleArtifact artifact_m1 = MyModuleArtifact.newModuleArtifact(descriptor_m1, packages_m1);

        // Define module:     m2
        // Can read:          java.base
        // Packages:          p2, m2_pinternal
        // Packages exported: none
        ModuleDescriptor descriptor_m2 =
                new ModuleDescriptor.Builder("m2")
                        .requires(md("java.base"))
                        .exports("p2", "m1")
                        .build();
        Set<String> packages_m2 = Stream.of("p2", "m2_pinternal").collect(Collectors.toSet());
        ModuleArtifact artifact_m2 = MyModuleArtifact.newModuleArtifact(descriptor_m2, packages_m2);

        // Set up a ModuleArtifactFinder containing all modules for this layer.
        ModuleArtifactFinder finder =
                new ModuleArtifactLibrary(artifact_m1, artifact_m2);

        // Resolves a module named "m1" that results in a configuration.  It
        // then augments that configuration with additional modules (and edges) induced
        // by service-use relationships.
        Configuration cf = Configuration.resolve(finder,
                                                 Layer.bootLayer(),
                                                 ModuleArtifactFinder.nullFinder(),
                                                 "m1");

        // map each module to differing class loaders for this test
        Map<ModuleArtifact, ClassLoader> map = new HashMap<>();
        map.put(artifact_m1, MyDiffClassLoader.loader1);
        map.put(artifact_m2, MyDiffClassLoader.loader2);

        // Create Layer that contains m1 & m2
        Layer layer = Layer.create(cf, map::get);

        // now use the same loader to load class p1.c1
        // NOTE: module m1 does not define a package named p1.
        //       p1 will be loaded in the unnamed module.
        Class p1_c1_class = MyDiffClassLoader.loader1.loadClass("p1.c1");
        try {
            p1_c1_class.newInstance();
            throw new RuntimeException("Failed to get IAE (p2 in m2 is exported to m1, not unqualifiedly");
        } catch (IllegalAccessError e) {
            System.out.println(e.getMessage());
            if (!e.getMessage().contains("not exported")) {
                throw new RuntimeException("Wrong message: " + e.getMessage());
            }
        }
    }

    static Requires md(String dn, Modifier... mods) {
        Set<Modifier> set = new HashSet<>();
        for (Modifier mod: mods)
            set.add(mod);
        return new Requires(set, dn);
    }

    public static void main(String args[]) throws Throwable {
      UmodNpkgDiffCL_PkgExpQualOther test = new UmodNpkgDiffCL_PkgExpQualOther();
      test.createLayerOnBoot();
    }
}
