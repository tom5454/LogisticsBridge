package com.tom.logisticsbridge;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.function.Function;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.google.common.collect.Maps;

public class ASMUtil {
	private static final ASMClassLoader LOADER = new ASMClassLoader();
	private static final HashMap<Method, Class<?>> cacheMethod = Maps.newHashMap();
	private static final HashMap<Field, Class<?>> cacheField = Maps.newHashMap();
	private static final String FUNC_DESC = Type.getInternalName(ObjFunc.class);
	private static final String GET_FUNC_DESC = Type.getMethodDescriptor(ObjFunc.class.getDeclaredMethods()[0]);
	//private static final String SET_FUNC_DESC = Type.getMethodDescriptor(ObjFunc.class.getDeclaredMethods()[1]);
	private static int IDs = 0;
	private static class ASMClassLoader extends ClassLoader
	{
		private ASMClassLoader()
		{
			super(ASMClassLoader.class.getClassLoader());
		}

		public Class<?> define(String name, byte[] data)
		{
			return defineClass(name, data, 0, data.length);
		}
	}
	@SuppressWarnings("unchecked")
	public static <I, O> Function<I, O> getfield(Field field){
		try {
			ObjFunc of = (ObjFunc) createWrapper(field).newInstance();
			return i -> (O) of.apply(i);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	/*public static <I, V> BiConsumer<I, V> setfield(Field field){
		try {
			ObjFunc of = (ObjFunc) createWrapper(field).newInstance();
			return (i, v) -> of.set(i, v);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}*/
	public static Class<?> createWrapper(Field callback)
	{
		if (cacheField.containsKey(callback))
		{
			return cacheField.get(callback);
		}

		ClassWriter cw = new ClassWriter(0);
		MethodVisitor mv;

		boolean isStatic = Modifier.isStatic(callback.getModifiers());
		String name = getUniqueName(callback);
		String desc = name.replace('.',  '/');
		String instType = Type.getInternalName(callback.getDeclaringClass());

		/*
        System.out.println("Name:     " + name);
        System.out.println("Desc:     " + desc);
        System.out.println("InstType: " + instType);
        System.out.println("Callback: " + callback.getName() + Type.getMethodDescriptor(callback));
        System.out.println("Event:    " + eventType);
		 */

		cw.visit(V1_6, ACC_PUBLIC | ACC_SUPER, desc, null, "java/lang/Object", new String[]{ FUNC_DESC });

		cw.visitSource(".dynamic", null);
		{
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitInsn(RETURN);
			mv.visitMaxs(2, 2);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC, "apply", GET_FUNC_DESC, null, null);
			mv.visitCode();
			if(!isStatic){
				mv.visitVarInsn(ALOAD, 1);
				mv.visitTypeInsn(CHECKCAST, instType);
			}
			mv.visitFieldInsn(isStatic ? GETSTATIC : GETFIELD, instType, callback.getName(), Type.getDescriptor(callback.getType()));
			mv.visitInsn(ARETURN);
			mv.visitMaxs(2, 2);
			mv.visitEnd();
		}
		/*{
			mv = cw.visitMethod(ACC_PUBLIC, "set", SET_FUNC_DESC, null, null);
			mv.visitCode();
			if(!isStatic){
				mv.visitVarInsn(ALOAD, 1);
				mv.visitTypeInsn(CHECKCAST, instType);
			}
			mv.visitVarInsn(ALOAD, 2);
			mv.visitTypeInsn(CHECKCAST, Type.getDescriptor(callback.getType()));
			mv.visitFieldInsn(isStatic ? PUTSTATIC : PUTFIELD, instType, callback.getName(), Type.getDescriptor(callback.getType()));
			mv.visitInsn(RETURN);
			mv.visitMaxs(2, 2);
			mv.visitEnd();
		}*/
		cw.visitEnd();
		Class<?> ret = LOADER.define(name, cw.toByteArray());
		cacheField.put(callback, ret);
		return ret;
	}

	private static String getUniqueName(Method callback)
	{
		return String.format("ASMUtil_%d_%s_%s_%s", IDs++,
				callback.getDeclaringClass().getSimpleName(),
				callback.getName(),
				callback.getParameterTypes()[0].getSimpleName());
	}
	private static String getUniqueName(Field callback)
	{
		return String.format("ASMUtil_%d_%s_%s_F", IDs++,
				callback.getDeclaringClass().getSimpleName(),
				callback.getName());
	}
	public static interface ObjFunc {
		Object apply(Object in);
		//void set(Object ins, Object val);
	}
	private static class ObjFuncTest implements ObjFunc {

		@Override
		public Object apply(Object in) {
			return A.b;
		}

		/*@Override
		public void set(Object ins, Object val) {
			A.b = (String) val;
		}*/
	}
	private static class A {
		public static String b;
		public String c;
	}
}
