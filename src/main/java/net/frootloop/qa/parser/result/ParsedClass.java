package net.frootloop.qa.parser.result;

import net.frootloop.qa.parser.StringParser;
import net.frootloop.qa.parser.result.internal.CodeTree;
import net.frootloop.qa.parser.result.internal.Visibility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

public class ParsedClass extends CodeTree {

    private Path filePath;
    private String packageName;
    private Visibility visibility;
    private String className;
    private int cohesionValue, cyclomaticComplexity = 1;

    private ArrayList<ParsedMethod> methods = new ArrayList<>();
    private ArrayList<String> parentClasses = new ArrayList<>();
    private ArrayList<String> classesReferenced = new ArrayList<>();

    private ArrayList<String> attributesDeclared;
    private int lcom = -1, wmc = -1;

    public ParsedClass(BlockOfCode classCodeBlock, String packageName, String[] importStatements, Path filePath){
        super(classCodeBlock);
        this.filePath = filePath;
        this.packageName = packageName;
        this.className = StringParser.getDeclaredClassName(this.root.leadingStatement);
        this.visibility = StringParser.getDeclaredClassVisibility(this.root.leadingStatement);
        this.cyclomaticComplexity = this.root.getCyclomaticComplexity();

        // Get list of methods"
        for (BlockOfCode child: this.root.children)
            if(StringParser.isMethodDeclaration(child.leadingStatement))
                this.methods.add(new ParsedMethod(child, this));

        // Set attributes:
        this.attributesDeclared = this.root.getDeclaredVariables();

        // Check if the package name we're given refers to a class we'd be embedded in:
        if(StringParser.getPackageClass(packageName) != null) this.addParent(packageName);

        // Set inheritance:
        for(String name : StringParser.getDeclaredClassInheritance(this.root.leadingStatement))
            this.addParent(this.getSignatureOfReferencedClass(name, importStatements));

        // Set references to other classes:
        String codeOfClass = this.root.getCodeAsString().replace("\n", "");
        for(String name : StringParser.getInitializedClassNames(codeOfClass))
            this.addReferenceTo(this.getSignatureOfReferencedClass(name, importStatements));
    }

    public ParsedClass(BlockOfCode classCodeBlock, String packageName, String[] importStatements){
        this(classCodeBlock,packageName,importStatements, null);
    }

    public void addMethod(ParsedMethod method) {
        if(method != null) this.methods.add(method);
    }

    public boolean hasAttributeCalled(String variableName) {
        return this.attributesDeclared.contains(variableName);
    }

    private String getSignatureOfReferencedClass(String className, String[] importStatements) {
        // Check if the class was imported from another package:
        for(String importedClassSignature : importStatements)
            if (StringParser.isStatementImportingClass(importedClassSignature, className))
                return importedClassSignature;
        // Otherwise, we can assume the class shares the same package as us:
        return this.packageName + "." + className;
    }

    private int getNumMethods() {
        int numMethods = this.methods.size();
        for(ParsedMethod m: this.methods)
            if(m.isStatic()) numMethods--;
        return numMethods;
    }

    public int getWeightedMethods() {
        if(this.wmc >= 0) return this.wmc;
        this.wmc = 0;
        for(ParsedMethod m: this.methods)
            this.wmc += m.getCyclomaticComplexity();
        return this.wmc;
    }

    public int getLackOfCohesionInMethods() {
        if(this.lcom >= 0) return this.lcom;
        this.lcom = Math.max(0, this.getNumMethodPairsAccessingDifferentAttributes() - this.getNumMethodPairsSharingAttributes());
        return this.lcom;
    }

    private int getNumMethodPairsAccessingDifferentAttributes() {
        return this.getNumMethods() - this.getNumMethodPairsSharingAttributes();
    }

    private int getNumMethodPairsSharingAttributes() {
        if(this.methods.size() < 2) return 0;

        int number = 0;
        for(ParsedMethod m1 : this.methods) {
            for(ParsedMethod m2 : this.methods) {
                if(m1 == m2) continue;
                boolean bothAccessSameAttributes = new HashSet<>(m1.getReferencedAttributes()).equals(new HashSet<>(m2.getReferencedAttributes()));
                if(bothAccessSameAttributes) number++;
            }
        }
        return number;
    }

    public String getSignature() {
        return packageName + "." + className;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String toString() {
        return this.getSignature();
    }

    public Path getFilePath() {
        if(this.filePath == null) return Path.of("./" + this.getSignature().replace(".", "/") + ".java");
        return filePath;
    }

    public ArrayList<String> getParentSignatures() {
        return this.parentClasses;
    }

    public void addParent(String classSignatureOfParent) {
        if(this.getSignature() != classSignatureOfParent && !parentClasses.contains(classSignatureOfParent))
            parentClasses.add(classSignatureOfParent);
    }

    public void addReferenceTo(String classSignatureReferenced) {
        if(this.getSignature() != classSignatureReferenced && !classesReferenced.contains(classSignatureReferenced))
            classesReferenced.add(classSignatureReferenced);
    }

    public ArrayList<String> getClassesReferencedDirectly() {
        return classesReferenced;
    }

    public int getCyclomatcComplexity() {return cyclomaticComplexity;}
}
