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

package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.PROTECTED;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;

import com.redhat.ceylon.compiler.java.util.Decl;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

/**
 * Builder for Java Classes. The specific properties of the "framework" of the
 * class like its name, superclass, interfaces etc can be set directly.
 * There are also three freely definable "zones" where any code can be inserted:
 * the "defs" that go at the top of the class body, the "body" that goes at
 * the bottom and the "init" the goes inside the constructor in the middle.
 * (the reason for these 3 zones is mostly historical, 2 would do just as well)
 * 
 * @author Tako Schotanus
 */
public class ClassDefinitionBuilder {
    private final AbstractTransformer gen;
    
    private final String name;
    
    private long modifiers;
    private long constructorModifiers = -1;
    
    private JCExpression extending;
    private JCStatement superCall;
    
    private final ListBuffer<JCExpression> satisfies = ListBuffer.lb();
    private final ListBuffer<JCTypeParameter> typeParams = ListBuffer.lb();
    private final ListBuffer<JCExpression> typeParamAnnotations = ListBuffer.lb();
    
    private final ListBuffer<JCAnnotation> annotations = ListBuffer.lb();
    
    private final ListBuffer<JCVariableDecl> params = ListBuffer.lb();
    
    private final ListBuffer<JCTree> defs = ListBuffer.lb();
    private final ListBuffer<JCTree> concreteInterfaceMemberDefs = ListBuffer.lb();
    private final ListBuffer<JCTree> body = ListBuffer.lb();
    private final ListBuffer<JCStatement> init = ListBuffer.lb();

    public static ClassDefinitionBuilder klass(AbstractTransformer gen, String name) {
        return new ClassDefinitionBuilder(gen, name);
    }
    
    public static ClassDefinitionBuilder methodWrapper(AbstractTransformer gen, String name, boolean shared) {
        return new ClassDefinitionBuilder(gen, name)
            .annotations(gen.makeAtMethod())
            .modifiers(FINAL, shared ? PUBLIC : 0)
            .constructorModifiers(PRIVATE);
    }

    private ClassDefinitionBuilder(AbstractTransformer gen, String name) {
        this.gen = gen;
        this.name = name;
        
        extending = getSuperclass(null);
        annotations(gen.makeAtCeylon());
    }

    public List<JCTree> build() {
        ListBuffer<JCTree> defs = ListBuffer.lb();
        appendDefinitionsTo(defs);
        if (!typeParamAnnotations.isEmpty()) {
            annotations(gen.makeAtTypeParameters(typeParamAnnotations.toList()));
        }
        JCTree.JCClassDecl klass = gen.make().ClassDef(
                gen.make().Modifiers(modifiers, annotations.toList()),
                gen.names().fromString(Util.quoteIfJavaKeyword(name)),
                typeParams.toList(),
                extending,
                satisfies.toList(),
                defs.toList());
        return List.<JCTree>of(klass);
    }

    private void appendDefinitionsTo(ListBuffer<JCTree> defs) {
        defs.appendList(this.defs);
        if ((modifiers & INTERFACE) == 0) {
            defs.append(createConstructor());
        }
        defs.appendList(body);
        if (!concreteInterfaceMemberDefs.isEmpty()) {
            JCTree.JCClassDecl concreteInterfaceKlass = gen.make().ClassDef(
                    gen.make().Modifiers((modifiers & PUBLIC) | FINAL | STATIC, gen.makeAtIgnore()),
                    gen.names().fromString(Util.getCompanionClassName(name)),
                    List.<JCTree.JCTypeParameter>nil(),
                    (JCTree)null,
                    List.<JCTree.JCExpression>nil(),
                    concreteInterfaceMemberDefs.toList());
            defs.append(concreteInterfaceKlass);
        }
    }

    private List<JCTree> appendConcreteInterfaceMembers(java.util.List<ProducedType> satisfies) {
        ListBuffer<JCTree> members = new ListBuffer<JCTree>();
        // FIXME: recurse in parent interfaces
        // FIXME: do not produce method if we override it
        for(ProducedType type : satisfies){
            TypeDeclaration decl = type.getDeclaration();
            for(Declaration member : decl.getMembers()){
                if(member instanceof Method && !member.isFormal()){
                    // this member has a body so we need to add a definition for it
                    MethodDefinitionBuilder methodBuilder = MethodDefinitionBuilder.method(gen, true, member.getName());
                    Method method = (Method) member;
                    ListBuffer<JCTree.JCExpression> params = ListBuffer.lb();
                    params.append(gen.makeUnquotedIdent("this"));
                    for(Parameter param : method.getParameterLists().get(0).getParameters()){
                        methodBuilder.parameter(param);
                        params.append(gen.makeUnquotedIdent(param.getName()));
                    }
                    
                    boolean isVoid = method.getType().getProducedTypeQualifiedName().equals("ceylon.language.Void");
                    JCMethodInvocation expr = gen.make().Apply(/*FIXME*/List.<JCTree.JCExpression>nil(), 
                            gen.makeQuotedQualIdentFromString(Util.getCompanionClassName(decl.getName())+"."+method.getName()), 
                            params.toList());
                    JCTree.JCStatement body;
                    if (!isVoid) {
                        methodBuilder.resultType(method);
                        body = gen.make().Return(expr);
                    }else{
                        body = gen.make().Exec(expr);
                    }
                    methodBuilder.body(body);
                    methodBuilder.modifiers(PUBLIC);
                    members.add(methodBuilder.build());
                }
            }
        }
        return members.toList();
    }

    private JCExpression getSuperclass(ProducedType extendedType) {
        JCExpression superclass;
        if (extendedType != null) {
            superclass = gen.makeJavaType(extendedType, CeylonTransformer.EXTENDS);
            // simplify if we can
// FIXME superclass.sym can be null
//            if (superclass instanceof JCTree.JCFieldAccess 
//            && ((JCTree.JCFieldAccess)superclass).sym.type == gen.syms.objectType) {
//                superclass = null;
//            }
        } else {
            if ((modifiers & INTERFACE) != 0) {
                // The VM insists that interfaces have java.lang.Object as their superclass
                superclass = gen.makeIdent(gen.syms().objectType);
            } else {
                superclass = null;
            }
        }
        return superclass;
    }

    private List<JCExpression> transformSatisfiedTypes(java.util.List<ProducedType> list) {
        if (list == null) {
            return List.nil();
        }

        ListBuffer<JCExpression> satisfies = new ListBuffer<JCExpression>();
        for (ProducedType t : list) {
            JCExpression jt = gen.makeJavaType(t, CeylonTransformer.SATISFIES);
            if (jt != null) {
                satisfies.append(jt);
            }
        }
        return satisfies.toList();
    }

    private JCMethodDecl createConstructor() {
        long mods = constructorModifiers;
        if (mods == -1) {
            // The modifiers were never explicitly set
            // so we try to come up with some good defaults
            mods = modifiers & (PUBLIC | PRIVATE | PROTECTED);
        }
        return MethodDefinitionBuilder
            .constructor(gen)
            .modifiers(mods)
            .parameters(params.toList())
            .body(superCall)
            .body(init.toList())
            .build();
    }
    
    /*
     * Builder methods - they transform the inner state before doing the final construction
     */
    
    public ClassDefinitionBuilder modifiers(long... modifiers) {
        long mods = 0;
        for (long mod : modifiers) {
            mods |= mod;
        }
        this.modifiers = mods;
        return this;
    }

    public ClassDefinitionBuilder constructorModifiers(long... constructorModifiers) {
        long mods = 0;
        for (long mod : constructorModifiers) {
            mods |= mod;
        }
        this.constructorModifiers = mods;
        return this;
    }

    public ClassDefinitionBuilder typeParameter(String name, java.util.List<ProducedType> satisfiedTypes, boolean covariant, boolean contravariant) {
        ListBuffer<JCExpression> bounds = new ListBuffer<JCExpression>();
        for (ProducedType t : satisfiedTypes) {
            if (!gen.willEraseToObject(t)) {
                bounds.append(gen.makeJavaType(t, AbstractTransformer.NO_PRIMITIVES));
            }
        }
        typeParams.append(gen.make().TypeParameter(gen.names().fromString(name),
                bounds.toList()));
        typeParamAnnotations.append(gen.makeAtTypeParameter(name, satisfiedTypes, covariant, contravariant));
        return this;
    }

    public ClassDefinitionBuilder typeParameter(Tree.TypeParameterDeclaration param) {
        gen.at(param);
        TypeParameter declarationModel = param.getDeclarationModel();
        return typeParameter(declarationModel.getName(), 
                declarationModel.getSatisfiedTypes(),
                declarationModel.isCovariant(),
                declarationModel.isContravariant());
    }

    public ClassDefinitionBuilder extending(ProducedType extendingType) {
        this.extending = getSuperclass(extendingType);
        annotations(gen.makeAtClass(extendingType));
        return this;
    }

    public ClassDefinitionBuilder extending(Tree.ExtendedType extendedType) {
        if (extendedType.getInvocationExpression().getPositionalArgumentList() != null) {
            List<JCExpression> args = List.<JCExpression> nil();

            for (Tree.PositionalArgument arg : extendedType.getInvocationExpression().getPositionalArgumentList().getPositionalArguments())
                args = args.append(gen.expressionGen().transformArg(arg.getExpression(), arg.getParameter(), false, null));

            superCall = gen.at(extendedType).Exec(gen.make().Apply(List.<JCExpression> nil(), gen.make().Ident(gen.names()._super), args));
        }
        return extending(extendedType.getType().getTypeModel());
    }
    
    public ClassDefinitionBuilder satisfies(java.util.List<ProducedType> satisfies) {
        this.satisfies.addAll(transformSatisfiedTypes(satisfies));
        this.defs.addAll(appendConcreteInterfaceMembers(satisfies));
        annotations(gen.makeAtSatisfiedTypes(satisfies));
        return this;
    }

    public ClassDefinitionBuilder annotations(List<JCTree.JCAnnotation> annotations) {
        this.annotations.appendList(annotations);
        return this;
    }

    private ClassDefinitionBuilder parameter(String name, TypedDeclaration decl, boolean isSequenced, boolean isDefaulted) {
        // Create a parameter for the constructor
        JCExpression type = gen.makeJavaType(decl);
        List<JCAnnotation> annots = List.nil();
        if (gen.needsAnnotations(decl)) {
            annots = annots.appendList(gen.makeAtName(name));
            if (isSequenced) {
                annots = annots.appendList(gen.makeAtSequenced());
            }
            if (isDefaulted) {
                annots = annots.appendList(gen.makeAtDefaulted());
            }
            annots = annots.appendList(gen.makeJavaTypeAnnotations(decl));
        }
        JCVariableDecl var = gen.make().VarDef(gen.make().Modifiers(0, annots), gen.names().fromString(name), type, null);
        params.append(var);
        
        // Check if the parameter is used outside of the initializer
        if (decl.isCaptured()) {
            // If so we create a field for it initializing it with the parameter's value
            JCVariableDecl localVar = gen.make().VarDef(gen.make().Modifiers(FINAL | PRIVATE), gen.names().fromString(name), type , null);
            defs.append(localVar);
            init.append(gen.make().Exec(gen.make().Assign(gen.makeSelect("this", localVar.getName().toString()), gen.make().Ident(var.getName()))));
        }
        
        return this;
    }
    
    public ClassDefinitionBuilder parameter(Tree.Parameter param) {
        gen.at(param);
        return parameter(param.getDeclarationModel());
    }
    
    public ClassDefinitionBuilder parameter(Parameter param) {
        String name = param.getName();
        return parameter(name, param, param.isSequenced(), param.isDefaulted());
    }
    
    public ClassDefinitionBuilder defs(JCTree statement) {
        if (statement != null) {
            this.defs.append(statement);
        }
        return this;
    }
    
    public ClassDefinitionBuilder defs(List<JCTree> defs) {
        if (defs != null) {
            this.defs.appendList(defs);
        }
        return this;
    }
    
    public ClassDefinitionBuilder body(JCTree statement) {
        if (statement != null) {
            this.body.append(statement);
        }
        return this;
    }
    
    public ClassDefinitionBuilder body(List<JCTree> body) {
        if (body != null) {
            this.body.appendList(body);
        }
        return this;
    }
    
    public ClassDefinitionBuilder init(JCStatement statement) {
        if (statement != null) {
            this.init.append(statement);
        }
        return this;
    }
    
    public ClassDefinitionBuilder init(List<JCStatement> init) {
        if (init != null) {
            this.init.appendList(init);
        }
        return this;
    }

    public ClassDefinitionBuilder concreteInterfaceMemberDefs(
            JCMethodDecl concreteInterfaceMember) {
        this.concreteInterfaceMemberDefs.append(concreteInterfaceMember);
        return this;
    }

    public ClassDefinitionBuilder field(int modifiers, String attrName, JCExpression type, JCExpression initialValue, boolean isLocal) {
        Name attrNameNm = gen.names().fromString(attrName);
        if (!isLocal) {
            // A shared or captured attribute gets turned into a class member
            defs(gen.make().VarDef(gen.make().Modifiers(modifiers, List.<JCTree.JCAnnotation>nil()), attrNameNm, type, null));
            if (initialValue != null) {
                // The attribute's initializer gets moved to the constructor
                // because it might be using locals of the initializer
                init(gen.make().Exec(gen.make().Assign(gen.makeSelect("this", attrName), initialValue)));
            }
        } else {
            // Otherwise it's local to the constructor
            init(gen.make().VarDef(gen.make().Modifiers(modifiers, List.<JCTree.JCAnnotation>nil()), attrNameNm, type, initialValue));
        }
        return this;
    }

    public ClassDefinitionBuilder method(Tree.AnyMethod method) {
        defs(gen.classGen().transform(method, this));
        if (method instanceof Tree.MethodDefinition) {
            Tree.MethodDefinition m = (Tree.MethodDefinition)method;
            if (Decl.withinInterface(m) && m.getBlock() != null) {
                concreteInterfaceMemberDefs(gen.classGen().transformConcreteInterfaceMember(m, ((com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface)Decl.container(method)).getType()));
            }
        }
        return this;
    }
}
