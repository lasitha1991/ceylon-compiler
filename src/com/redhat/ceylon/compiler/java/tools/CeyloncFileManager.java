/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 */

package com.redhat.ceylon.compiler.java.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.redhat.ceylon.compiler.java.codegen.CeylonFileObject;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacFileManager;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

public class CeyloncFileManager extends JavacFileManager implements StandardJavaFileManager {
    private Module currentModule;
    private JarOutputRepositoryManager jarRepository;
    private Context context;
    private Options options;
    private List<File> repositories;

    public CeyloncFileManager(Context context, boolean register, Charset charset) {
        super(context, register, charset);
        options = Options.instance(context);
        jarRepository = new JarOutputRepositoryManager(CeylonLog.instance(context), options, this);
    }

    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
        super.setContext(context);
    }

    protected JavaFileObject.Kind getKind(String extension) {
        if (extension.equals(JavaFileObject.Kind.CLASS.extension))
            return JavaFileObject.Kind.CLASS;
        else if (/*extension.equals(JavaFileObject.Kind.SOURCE.extension) || */extension.equals(".ceylon"))
            return JavaFileObject.Kind.SOURCE;
        else if (extension.equals(JavaFileObject.Kind.HTML.extension))
            return JavaFileObject.Kind.HTML;
        else
            return JavaFileObject.Kind.OTHER;
    }

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(final Context context) {
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            public JavaFileManager make() {
                return new CeyloncFileManager(context, true, null);
            }
        });
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {

        Iterable<? extends JavaFileObject> theCollection = super.getJavaFileObjectsFromFiles(files);
        ArrayList<JavaFileObject> result = new ArrayList<JavaFileObject>();
        for (JavaFileObject file : theCollection) {
            if (file.getName().endsWith(".ceylon")) {
                result.add(new CeylonFileObject(file));
            } else {
                result.add(file);
            }
        }
        return result;
    }

    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        Iterable<JavaFileObject> result = super.list(location, packageName, kinds, recurse);
        ListBuffer<JavaFileObject> buf = new ListBuffer<JavaFileObject>();
        for (JavaFileObject f : result) {
            if (f.getName().endsWith(".ceylon")) {
                buf.add(new CeylonFileObject(f));
            } else {
                buf.add(f);
            }
        }
        return buf.toList();
    }

    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof CeylonFileObject) {
            CeylonFileObject fo = (CeylonFileObject) file;
            return super.inferBinaryName(location, fo.getFile());
        }
        return super.inferBinaryName(location, file);
    }

    @Override
    protected JavaFileObject getFileForOutput(Location location, final String fileName, FileObject sibling) throws IOException {
        if (sibling instanceof CeylonFileObject) {
            sibling = ((CeylonFileObject) sibling).getFile();
        }
        if(location == StandardLocation.CLASS_OUTPUT){
            File dir = getOutputFolder(sibling);
            File siblingFile = null;
            if (sibling != null && sibling instanceof RegularFileObject) {
                siblingFile = ((RegularFileObject)sibling).getUnderlyingFile();
            }
            return jarRepository.getFileObject(dir, currentModule, fileName, siblingFile);
        }else
            return super.getFileForOutput(location, fileName, sibling);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location,
            String className,
            JavaFileObject.Kind kind) throws IOException {
        nullCheck(location);
        // validateClassName(className);
        nullCheck(className);
        nullCheck(kind);
        if (!sourceOrClass.contains(kind))
            throw new IllegalArgumentException("Invalid kind " + kind);
        String fileName = externalizeFileName(className, kind);
        JavaFileObject file = getFileForInput(location, fileName);
        if (file != null && fileName.endsWith(".ceylon")) {
            return new CeylonFileObject(file);
        } else {
            return file;
        }

    }

    private String externalizeFileName(String className, JavaFileObject.Kind kind){
        String extension;
        if(kind == Kind.SOURCE)
            extension = ".ceylon";
        else
            extension = kind.extension;
        return externalizeFileName(className) + extension;
    }

    @Override
    public void flush() {
        super.flush();
        jarRepository.flush();
    }
    
    public void setModule(Module module) {
        currentModule = module;
    }
    
    private File getOutputFolder(FileObject sibling){
        if (getClassOutDir() != null) {
            return getClassOutDir();
        } else {
            File siblingDir = null;
            if (sibling != null && sibling instanceof RegularFileObject) {
                siblingDir = ((RegularFileObject)sibling).getUnderlyingFile().getParentFile();
            }
            return siblingDir;
        }
    }
    
    @Override
    public Iterable<? extends File> getLocation(Location location) {
        if(location != CeylonLocation.REPOSITORY)
            return super.getLocation(location);
        return getRepositories();
    }

    private Iterable<? extends File> getRepositories() {
        // caching
        if(repositories != null)
            return repositories;
        // lazy loading
        List<String> repositoryNames = options.getMulti(OptionName.CEYLONREPO);
        repositoryNames = Util.addDefaultRepositories(repositoryNames);
        repositories = new ArrayList<File>(repositoryNames.size());
        for(String repository : repositoryNames)
            repositories.add(new File(repository));
        return repositories;
    }
    
    @Override
    protected File getClassOutDir() {
        File outDir = super.getClassOutDir();
        // set the default value for Ceylon
        if(outDir == null)
            classOutDir = new File("modules");
        return outDir;
    }
}