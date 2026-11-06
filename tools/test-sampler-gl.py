#!/usr/bin/env python3
"""Real EGL/OpenGL sampler+postprocess smoke using assignments from Lucerna's Java regression.
This does not launch Minecraft, construct its world pipeline, or validate LOD geometry.
"""
from contextlib import contextmanager
import ctypes as C
import ctypes.util
import json
import os
import sys
from pathlib import Path

os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
EGL = C.CDLL(ctypes.util.find_library('EGL') or 'libEGL.so.1')
GL = C.CDLL(ctypes.util.find_library('GL') or 'libGL.so.1')

def api(lib, name, result, *arguments):
    fn = getattr(lib, name)
    fn.restype, fn.argtypes = result, arguments
    return fn

ptr, integer, uint, enum = C.c_void_p, C.c_int, C.c_uint, C.c_uint
get_display = api(EGL, 'eglGetDisplay', ptr, ptr)
initialize = api(EGL, 'eglInitialize', uint, ptr, C.POINTER(integer), C.POINTER(integer))
bind_api = api(EGL, 'eglBindAPI', uint, enum)
choose_config = api(EGL, 'eglChooseConfig', uint, ptr, C.POINTER(integer), C.POINTER(ptr), integer, C.POINTER(integer))
create_surface = api(EGL, 'eglCreatePbufferSurface', ptr, ptr, ptr, C.POINTER(integer))
create_context = api(EGL, 'eglCreateContext', ptr, ptr, ptr, ptr, C.POINTER(integer))
make_current = api(EGL, 'eglMakeCurrent', uint, ptr, ptr, ptr, ptr)
destroy_context = api(EGL, 'eglDestroyContext', uint, ptr, ptr)
destroy_surface = api(EGL, 'eglDestroySurface', uint, ptr, ptr)
terminate = api(EGL, 'eglTerminate', uint, ptr)
get_string = api(GL, 'glGetString', C.c_char_p, enum)
get_integer = api(GL, 'glGetIntegerv', None, enum, C.POINTER(integer))
get_error = api(GL, 'glGetError', enum)
create_shader = api(GL, 'glCreateShader', uint, enum)
shader_source = api(GL, 'glShaderSource', None, uint, integer, C.POINTER(C.c_char_p), C.POINTER(integer))
compile_shader = api(GL, 'glCompileShader', None, uint)
shader_status = api(GL, 'glGetShaderiv', None, uint, enum, C.POINTER(integer))
shader_log = api(GL, 'glGetShaderInfoLog', None, uint, integer, C.POINTER(integer), ptr)
delete_shader = api(GL, 'glDeleteShader', None, uint)
create_program = api(GL, 'glCreateProgram', uint)
attach_shader = api(GL, 'glAttachShader', None, uint, uint)
link_program = api(GL, 'glLinkProgram', None, uint)
program_status = api(GL, 'glGetProgramiv', None, uint, enum, C.POINTER(integer))
program_log = api(GL, 'glGetProgramInfoLog', None, uint, integer, C.POINTER(integer), ptr)
use_program = api(GL, 'glUseProgram', None, uint)
delete_program = api(GL, 'glDeleteProgram', None, uint)
uniform_location = api(GL, 'glGetUniformLocation', integer, uint, C.c_char_p)
uniform_int = api(GL, 'glUniform1i', None, integer, integer)
gen_textures = api(GL, 'glGenTextures', None, integer, C.POINTER(uint))
delete_textures = api(GL, 'glDeleteTextures', None, integer, C.POINTER(uint))
active_texture = api(GL, 'glActiveTexture', None, enum)
bind_texture = api(GL, 'glBindTexture', None, enum, uint)
tex_parameter = api(GL, 'glTexParameteri', None, enum, enum, integer)
tex_image = api(GL, 'glTexImage2D', None, enum, integer, integer, integer, integer, integer, enum, enum, ptr)
viewport = api(GL, 'glViewport', None, integer, integer, integer, integer)
begin = api(GL, 'glBegin', None, enum)
vertex = api(GL, 'glVertex2f', None, C.c_float, C.c_float)
end = api(GL, 'glEnd', None)
read_pixels = api(GL, 'glReadPixels', None, integer, integer, integer, integer, enum, enum, ptr)

def limit(value):
    result = integer()
    get_integer(value, C.byref(result))
    return result.value

def shader(kind, source):
    handle = create_shader(kind)
    encoded = C.c_char_p(source.encode())
    shader_source(handle, 1, C.byref(encoded), None)
    compile_shader(handle)
    result = integer()
    shader_status(handle, 0x8B81, C.byref(result))
    if not result.value:
        log = C.create_string_buffer(65536)
        shader_log(handle, len(log), None, log)
        delete_shader(handle)
        raise AssertionError(log.value.decode())
    return handle

def smoke(case):
    units = case['units']
    names = list(units)
    assert all(0 <= unit < limit(0x8872) for unit in units.values()), case
    total = sum(range(1, len(names) + 1))
    terms = [f'texture2D({name}, vec2(0.5)).rgb * {float(i + 1)}' for i, name in enumerate(names)]
    fragment = '#version 120\n' + '\n'.join(f'uniform sampler2D {name};' for name in names)
    fragment += '\nvoid main(){gl_FragColor=vec4((' + '+'.join(terms) + f')/{float(total)}*vec3(0.7,0.8,0.9)+vec3(0.02),1.0);}}'
    vs = shader(0x8B31, '#version 120\nvoid main(){gl_Position=gl_Vertex;}')
    fs = shader(0x8B30, fragment)
    program = create_program()
    textures = (uint * len(set(units.values())))()
    try:
        attach_shader(program, vs); attach_shader(program, fs); link_program(program)
        result = integer(); program_status(program, 0x8B82, C.byref(result))
        if not result.value:
            log = C.create_string_buffer(65536); program_log(program, len(log), None, log)
            raise AssertionError(log.value.decode())
        use_program(program)
        gen_textures(len(textures), textures)
        colors = {}
        for unit, texture in zip(sorted(set(units.values())), textures):
            color = [0.05 + unit * 0.01, 0.1 + unit * 0.006, 0.2 + unit * 0.003, 1.0]
            colors[unit] = color
            active_texture(0x84C0 + unit); bind_texture(0x0DE1, texture)
            tex_parameter(0x0DE1, 0x2801, 0x2600); tex_parameter(0x0DE1, 0x2800, 0x2600)
            data = (C.c_float * 4)(*color)
            tex_image(0x0DE1, 0, 0x8814, 1, 1, 0, 0x1908, 0x1406, data)
        for name, unit in units.items():
            location = uniform_location(program, name.encode())
            assert location >= 0, name
            uniform_int(location, unit)
        viewport(0, 0, 1, 1)
        begin(0x0007)
        for x, y in [(-1, -1), (1, -1), (1, 1), (-1, 1)]: vertex(x, y)
        end()
        pixel = (C.c_float * 4)(); read_pixels(0, 0, 1, 1, 0x1908, 0x1406, pixel)
        expected = [sum(colors[units[name]][channel] * (i + 1) for i, name in enumerate(names)) / total * scale + 0.02
                    for channel, scale in enumerate([0.7, 0.8, 0.9])]
        assert max(abs(pixel[i] - expected[i]) for i in range(3)) < 2 / 255, (case['name'], list(pixel), expected)
        assert get_error() == 0, case['name']
        print(f"Native sampler/postprocess PASS: {case['name']}; pixel={list(pixel)[:3]}")
    finally:
        use_program(0); delete_textures(len(textures), textures)
        delete_program(program); delete_shader(vs); delete_shader(fs)

@contextmanager
def context():
    display = get_display(None)
    major, minor = integer(), integer()
    assert display and initialize(display, C.byref(major), C.byref(minor)), 'EGL initialization failed'
    surface = context = None
    try:
        assert bind_api(0x30A2), 'Desktop OpenGL API unavailable'
        config, count = ptr(), integer()
        attributes = (integer * 13)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
        assert choose_config(display, attributes, C.byref(config), 1, C.byref(count)) and count.value
        surface = create_surface(display, config, (integer * 5)(0x3057, 1, 0x3056, 1, 0x3038))
        context = create_context(display, config, None, (integer * 1)(0x3038))
        assert surface and context and make_current(display, surface, surface, context), 'OpenGL context creation failed'
        print('OpenGL:', get_string(0x1F02).decode(), 'renderer:', get_string(0x1F01).decode())
        print('Driver sampler limits:', json.dumps({'fragment': limit(0x8872), 'combined': limit(0x8B4D), 'vertex': limit(0x8B4C)}))
        yield
    finally:
        make_current(display, None, None, None)
        if context: destroy_context(display, context)
        if surface: destroy_surface(display, surface)
        terminate(display)

def main():
    cases = json.loads(Path(sys.argv[1]).read_text()) if len(sys.argv) > 1 else [
        {'name': 'environment-diagnostic-only', 'units': {'colortex0': 0, 'dhDepthTex0': 3, 'dhDepthTex1': 4}}]
    with context():
        for case in cases: smoke(case)
    print(f'Native sampler/postprocess cases passed: {len(cases)}; Minecraft/LOD validation not performed')

if __name__ == '__main__': main()
