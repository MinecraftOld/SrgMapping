package cn.frish2021;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.objectweb.asm.Opcodes.ASM9;

public class Main {
    private static final Map<String, String> ClassObfToCLean = new HashMap<String, String>();
    private static final Map<String, String> ClassCleanToObf = new HashMap<String, String>();
    private static final Map<String, String> FieldObfToCLean = new HashMap<String, String>();
    private static final Map<String, String> FieldCleanToObf = new HashMap<String, String>();
    private static final Map<String, String> MethodObfToCLean = new HashMap<String, String>();
    private static final Map<String, String> MethodCleanToObf = new HashMap<String, String>();
    private static final Map<String, String> map = new HashMap<String, String>();
    private static final Map<String, Set<String>> MethodMap = new HashMap<String, Set<String>>();

    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);

        System.out.println("输入你的srg混淆映射路径：");
        if (scan.hasNext()) {
            String str1 = scan.next();
            Scanner scan1 = new Scanner(System.in);
            System.out.println("输入你要反混淆的jar路径：");
            if (scan1.hasNext()) {
                String str2 = scan1.next();
                Scanner scan2 = new Scanner(System.in);
                System.out.println("输入jar的输出目录路径：");
                if (scan2.hasNext()) {
                    String str3 = scan2.next();
                    try {
                        onMapping(str1,str2,str3);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                scan2.close();
            }
            scan1.close();
        }
        scan.close();
    }

    public static void onMapping(String SrgPath,String obfile,String obfout) throws IOException {
        List<String> f = Files.readAllLines(new File(SrgPath).toPath());

        //开始解析
        for (String line : f) {
            //Package(不需要反混淆)
            //PK: . net/minecraft/src

            if (line.startsWith("CL: ")) {
                //Class
                //CL: aaa net/minecraft/src/TileEntityFurnace
                //(类名)类型: 混淆后名 混淆前名
                String ClassName = line.replace("CL: ", "");//删除CL前缀
                String Class_Obf = ClassName.substring(0, ClassName.indexOf(" "));
                if (Class_Obf.equals("net/minecraft/client/MinecraftApplet") || Class_Obf.equals("net/minecraft/client/Minecraft")) {
                    continue;
                }
                String ClassName1 = ClassName.substring(0, ClassName.indexOf(" "));
                String Class_Clean = ClassName.substring(ClassName1.length() + 1, ClassName.length());
                ClassCleanToObf.put(Class_Clean, Class_Obf);
                ClassObfToCLean.put(Class_Obf, Class_Clean);
            } else if (line.startsWith("FD: ")) {
                //Field
                //FD: aaa/c net/minecraft/src/TileEntityFurnace/furnaceCookTime
                //(变量)类型: 混淆后所属的类名/混淆后变量名 混淆前所属的类名/混淆后变量名
                //解析变量名比解析类名更复杂
                String FieldName = line.replace("FD: ", "");
                String FieldName_Obf = FieldName.substring(0, FieldName.indexOf(" "));
                String FieldName_Clean = FieldName.replace(FieldName_Obf + " ", "");
                FieldCleanToObf.put(FieldName_Clean, FieldName_Obf);
                FieldObfToCLean.put(FieldName_Obf, FieldName_Clean);
            } else if (line.startsWith("MD: ")) {
                //Method
                //MD: aaa/b (I)I net/minecraft/src/TileEntityFurnace/getCookProgressScaled (I)I
                //(方法)类型: 混淆后所属的类名/混淆后方法名 (混淆后的参数类型)混淆后的返回类型 混淆前所属的类名/混淆前的方法名 (混淆前的参数类型)混淆前的返回类型
                //解析方法比解析变量名更TM复杂
                String MethodName = line.replace("MD: ", "");
                String MethodObf = MethodName.substring(0, MethodName.indexOf(" net")).trim();//混淆名 + 参数类型 + 返回类型
                String field = MethodName.substring(MethodObf.length(), MethodName.length()); //一个变量简化代码用的
                String MethodCLean = field.trim(); //混淆前的名字 + 参数类型 + 返回类型
                //混淆方法名
                String MethodObf_Name = MethodName.substring(0, MethodName.indexOf(" ")).trim();
                //未混淆名
                String MethodClean_Name = MethodCLean.substring(0, MethodCLean.indexOf(" ")).trim();
                //获取混淆参数组
                String MethodObf_Args = MethodObf.replace(MethodObf_Name, "").trim();
                //获取混淆前的参数组
                String MethodClean_Args = MethodCLean.replace(MethodClean_Name, "").trim();
                //获取混淆最终参数
                String MethodObf_Arg = MethodObf_Args.substring(0,MethodObf_Args.indexOf(")") + 1).trim();
                //获取混淆前最终参数
                String MethodClean_Arg = MethodClean_Args.substring(0,MethodClean_Args.indexOf(")") + 1).trim();
                //获取混淆返回
                String MethodObf_Return = MethodObf.replace(MethodObf_Arg,"").replace(MethodObf_Name,"").trim();
                //获取混淆前返回
                String MethodClean_Return = MethodCLean.replace(MethodClean_Arg,"").replace(MethodClean_Name,"").trim();
                //混淆前汇总值
                //混淆类名/方法名 (L参数;)返回类型; |||||| 混淆前类名/方法名 (L参数;)返回类型;
                String MethodOToC = MethodClean_Name + " " + MethodClean_Arg + MethodClean_Return + "";
                String MethodCToO = MethodObf_Name + " " + MethodObf_Arg + MethodObf_Return + "";
                MethodObfToCLean.put(MethodCToO, MethodOToC);
                MethodCleanToObf.put(MethodOToC, MethodCToO);
            }
        }

        analyze(obfile);

        //Class Map
        map.putAll(ClassObfToCLean);

        //Field Map
        for (Map.Entry<String, String> stringStringEntry : FieldObfToCLean.entrySet()) {
            String className = stringStringEntry.getKey().substring(0, stringStringEntry.getKey().lastIndexOf("/"));
            String fieldName = stringStringEntry.getKey().substring(stringStringEntry.getKey().lastIndexOf("/") + 1);
            map.put(className + "." + fieldName, stringStringEntry.getValue().substring(stringStringEntry.getValue().lastIndexOf("/") + 1));
        }

        //method map
        for (Map.Entry<String, String> stringStringEntry : MethodObfToCLean.entrySet()) {
            String[] methodObfSplit = stringStringEntry.getKey().split(" ");
            String[] methodCleanSplit = stringStringEntry.getValue().split(" ");
            String methodObfClass = methodObfSplit[0].substring(0, methodObfSplit[0].lastIndexOf("/"));
            String methodObfName = methodObfSplit[0].substring(methodObfSplit[0].lastIndexOf("/") + 1);
            String methodCleanName = methodCleanSplit[0].substring(methodCleanSplit[0].lastIndexOf("/") + 1);
            map.put(methodObfClass + "." + methodObfName + methodObfSplit[1], methodCleanName);
        }

        //反混淆用的
        JarFile jarFile = new JarFile(obfile);
        Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries();
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(obfout));
        while (jarEntryEnumeration.hasMoreElements()) {
            JarEntry jarEntry = jarEntryEnumeration.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                ClassReader cr = new ClassReader(jarFile.getInputStream(jarEntry));
                ClassWriter classWriter = new ClassWriter(0);
                String substring = jarEntry.getName().substring(0, jarEntry.getName().lastIndexOf(".class"));

                ClassRemapper classRemapper = new ClassRemapper(new ClassVisitor(ASM9, classWriter) {
                }, new SimpleRemapper(map) {
                    @Override
                    public String mapFieldName(String owner, String name, String descriptor) {
                        String remappedName = map(owner + '.' + name);

                        if (remappedName == null) {
                            if (MethodMap.get(owner) != null) {
                                for (String s : MethodMap.get(owner)) {
                                    String rn = mapFieldName(s, name, descriptor);
                                    if (rn != null) {
                                        return rn;
                                    }
                                }
                            }
                        }

                        return remappedName == null ? name : remappedName;
                    }

                    @Override
                    public String mapMethodName(String owner, String name, String descriptor) {
                        String remappedName = map(owner + '.' + name + descriptor);

                        if (remappedName == null) {
                            if (MethodMap.get(owner) != null) {
                                for (String s : MethodMap.get(owner)) {
                                    String rn = mapMethodName(s, name, descriptor);
                                    if (rn != null) {
                                        return rn;
                                    }
                                }
                            }
                        }

                        return remappedName == null ? name : remappedName;
                    }
                });

                cr.accept(classRemapper, 0);
                JarEntry classEntry = new JarEntry(map.getOrDefault(substring, substring) + ".class");
                jarOutputStream.putNextEntry(classEntry);
                jarOutputStream.write(classWriter.toByteArray());

            } else {
                JarEntry file = new JarEntry(jarEntry.getName());
                jarOutputStream.putNextEntry(file);
                jarOutputStream.write(IOUtils.toByteArray(jarFile.getInputStream(jarEntry)));
            }
        }
        jarOutputStream.closeEntry();
        jarOutputStream.close();
    }

    private static void analyze(String name) throws IOException {
        JarFile jarFile = new JarFile(name);
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                ClassReader classReader = new ClassReader(jarFile.getInputStream(jarEntry));
                classReader.accept(new ClassVisitor(ASM9) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        Set<String> strings = new HashSet<String>();
                        if (MethodMap.containsKey(name)) {
                            if (superName != null) {
                                if (!MethodMap.get(name).contains(superName)) {
                                    strings.add(superName);
                                }
                            }

                            if (interfaces != null) {
                                for (String anInterface : interfaces) {
                                    if (!MethodMap.get(name).contains(anInterface)) {
                                        strings.add(anInterface);
                                    }
                                }
                            }

                            MethodMap.get(name).addAll(strings);

                        } else {
                            if (superName != null) {
                                strings.add(superName);
                            }

                            if (interfaces != null) {
                                Collections.addAll(strings, interfaces);
                            }

                            MethodMap.put(name, strings);
                        }
                        super.visit(version, access, name, signature, superName, interfaces);
                    }
                }, 0);
            }
        }
    }
}
