/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.compiler.loader.impl.reflect.model;

import java.util.List;

import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.impl.reflect.ReflectionModelLoader;
import com.redhat.ceylon.compiler.loader.model.LazyModuleManager;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Modules;

public class ReflectionModuleManager extends LazyModuleManager {

    private AbstractModelLoader modelLoader;

    public ReflectionModuleManager(Context context) {
        super(context);
    }

    @Override
    public void initCoreModules() {
        super.initCoreModules();
        Modules modules = getContext().getModules();
        // FIXME: this should go away somewhere else, but we need it to be set otherwise
        // when we load the module from compiled sources, ModuleManager.getOrCreateModule() will not
        // return the language module because its version is null
        Module languageModule = modules.getLanguageModule();
        languageModule.setVersion(TypeChecker.LANGUAGE_MODULE_VERSION);
    }
    
    @Override
    public AbstractModelLoader getModelLoader() {
        if(modelLoader == null){
            Modules modules = getContext().getModules();
            modelLoader = new ReflectionModelLoader(this, modules);            
        }
        return modelLoader;
    }

    @Override
    protected Module createModule(List<String> moduleName) {
        Module module;
        if(isModuleLoadedFromSource(Util.getName(moduleName)))
            module = new Module();
        else
            module = new ReflectionModule(this);
        module.setName(moduleName);
        return module;
    }

    @Override
    public void prepareForTypeChecking() {
        if(!isModuleLoadedFromSource("ceylon.language"))
            getModelLoader().loadStandardModules();
        getModelLoader().loadPackageDescriptors();
    }
}
