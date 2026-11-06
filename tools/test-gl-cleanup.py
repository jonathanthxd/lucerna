#!/usr/bin/env python3
"""Fault-inject GL calls around real production classes; this is NOT a GPU test."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'org/apache/logging/log4j/Logger.java': '''package org.apache.logging.log4j;
public class Logger { public void warn(String message, Object... args) {} }''',
    'org/apache/logging/log4j/LogManager.java': '''package org.apache.logging.log4j;
public class LogManager { public static Logger getLogger(Class<?> type) { return new Logger(); } }''',
    'org/lwjgl/opengl/GL20C.java': '''package org.lwjgl.opengl;
public class GL20C { public static final int GL_COMPILE_STATUS=1, GL_LINK_STATUS=2, GL_TRUE=1; }''',
    'org/lwjgl/opengl/KHRDebug.java': '''package org.lwjgl.opengl;
public class KHRDebug { public static final int GL_SHADER=1, GL_PROGRAM=2; }''',
    'org/lwjgl/opengl/GL20.java': '''package org.lwjgl.opengl;
public class GL20 {
 public static boolean compile=true, link=true, sourceFailure, attachFailure;
 public static int shadersDeleted, programsDeleted, next=10;
 public static int glCreateShader(int type) { return next++; }
 public static void glCompileShader(int id) {}
 public static int glGetShaderi(int id, int key) { return compile ? 1 : 0; }
 public static void glDeleteShader(int id) { shadersDeleted++; }
 public static int glCreateProgram() { return next++; }
 public static void glAttachShader(int program, int shader) { if (attachFailure) throw new IllegalStateException("attach failure"); }
 public static void glLinkProgram(int id) {}
 public static int glGetProgrami(int id, int key) { return link ? 1 : 0; }
 public static void glDeleteProgram(int id) { programsDeleted++; }
}''',
    'us/z1x/lucerna/gl/GLDebug.java': '''package us.z1x.lucerna.gl;
public class GLDebug { public static void nameObject(int type, int id, String name) {} }''',
    'us/z1x/lucerna/gl/LucernaRenderSystem.java': '''package us.z1x.lucerna.gl;
public class LucernaRenderSystem {
 public static String getShaderInfoLog(int id) { return "synthetic compile log"; }
 public static String getProgramInfoLog(int id) { return "synthetic link log"; }
 public static void bindAttributeLocation(int id, int index, String name) {}
 public static void detachShader(int program, int shader) {}
}''',
    'us/z1x/lucerna/gl/shader/ShaderType.java': '''package us.z1x.lucerna.gl.shader;
public enum ShaderType { VERTEX(1), FRAGMENT(2); public final int id; ShaderType(int id) { this.id=id; } }''',
    'us/z1x/lucerna/gl/shader/ShaderWorkarounds.java': '''package us.z1x.lucerna.gl.shader;
public class ShaderWorkarounds { public static void safeShaderSource(int handle, String source) {
 if (org.lwjgl.opengl.GL20.sourceFailure) throw new IllegalStateException("source failure");
} }''',
    'GlCleanupRegression.java': '''import org.lwjgl.opengl.GL20;
import us.z1x.lucerna.gl.shader.*;
public class GlCleanupRegression {
 static int checks;
 static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
 static RuntimeException fails(Runnable action) {
  try { action.run(); throw new AssertionError("Expected failure"); } catch (RuntimeException expected) { return expected; }
 }
 public static void main(String[] args) {
  GL20.compile=false;
  RuntimeException error=fails(() -> new GlShader(ShaderType.VERTEX, "dh_terrain.vsh", "source"));
  check(GL20.shadersDeleted==1);
  check(error.getMessage().contains("dh_terrain.vsh"));
  check(error.getMessage().contains("synthetic compile log"));
  GL20.compile=true; GL20.sourceFailure=true;
  fails(() -> new GlShader(ShaderType.VERTEX, "source.vsh", "source"));
  check(GL20.shadersDeleted==2);
  GL20.sourceFailure=false;
  GlShader vertex=new GlShader(ShaderType.VERTEX,"terrain.vsh","source");
  check(GL20.shadersDeleted==2);
  GL20.link=false;
  error=fails(() -> ProgramCreator.create("dh_water",vertex));
  check(GL20.programsDeleted==1);
  check(GL20.shadersDeleted==2); // caller still owns the shader
  check(error.getMessage().contains("dh_water"));
  check(error.getMessage().contains("synthetic link log"));
  GL20.link=true; GL20.attachFailure=true;
  fails(() -> ProgramCreator.create("attach",vertex));
  check(GL20.programsDeleted==2);
  GL20.attachFailure=false;
  int program=ProgramCreator.create("success",vertex);
  check(GL20.programsDeleted==2);
  GL20.glDeleteProgram(program); vertex.destroy();
  check(GL20.programsDeleted==3 && GL20.shadersDeleted==3);
  System.out.println("GL handle cleanup fault-injection checks passed: " + checks);
 }
}''',
}

with tempfile.TemporaryDirectory(prefix='lucerna-gl-cleanup-') as temp:
    output = Path(temp)
    sources = []
    for relative, content in STUBS.items():
        file = output / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
        sources.append(str(file))
    for relative in ['gl/GlResource.java', 'gl/shader/GlShader.java', 'gl/shader/ProgramCreator.java']:
        sources.append(str(ROOT / 'src/main/java/us/z1x/lucerna' / relative))
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', temp, *sources], check=True)
    subprocess.run(['java', '-cp', temp, 'GlCleanupRegression'], check=True)
