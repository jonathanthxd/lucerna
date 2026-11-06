#!/usr/bin/env python3
"""Optional probe of an original Mellow ZIP: expand includes/preprocess, compile/link fullscreen
programs in real desktop GL and record ACTIVE sampler signatures. Not a Minecraft pipeline test.
No shader source or ZIP is redistributed by the report.
"""
import ctypes as C
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import zipfile

spec = importlib.util.spec_from_file_location('sampler_gl', Path(__file__).with_name('test-sampler-gl.py'))
gl = importlib.util.module_from_spec(spec); spec.loader.exec_module(gl)

def preprocess(archive, path, dh):
    def expand(name, stack=()):
        if name in stack: raise ValueError('Include cycle: ' + name)
        def include(match):
            target = match.group(1)
            if target.startswith('/'): target = 'shaders' + target
            else: target = str(PurePosixPath(name).parent / target)
            return expand(target, (*stack, name))
        return re.sub(r'^\s*#include\s+"([^"]+)"\s*$', include, archive.read(name).decode('utf-8-sig'), flags=re.M)
    source = expand(path)
    version = re.search(r'^\s*#version[^\n]+', source, re.M).group().strip()
    extensions = re.findall(r'^\s*#extension[^\n]+', source, re.M)
    source = re.sub(r'^\s*#(?:version|extension)[^\n]*', '', source, flags=re.M)
    defines = ['-DMC_VERSION=10809', '-DIS_IRIS', '-DMC_GL_VERSION=450', '-DMC_GLSL_VERSION=450']
    if dh: defines.append('-DDISTANT_HORIZONS')
    materials = ['UNKNOWN','LEAVES','STONE','WOOD','METAL','DIRT','LAVA','DEEPSLATE','SNOW','SAND','TERRACOTTA','NETHER_STONE','WATER','GRASS','AIR','ILLUMINATED']
    defines.extend(f'-DDH_BLOCK_{name}={i}' for i,name in enumerate(materials))
    out = subprocess.run(['gcc','-E','-P','-undef','-x','c',*defines,'-'], input=source, text=True, capture_output=True, check=True)
    return version + '\n' + '\n'.join(extensions) + '\n' + out.stdout

def main():
    pack=Path(sys.argv[1]); report=Path(sys.argv[2])
    result={'zip_sha256':hashlib.sha256(pack.read_bytes()).hexdigest(), 'probe':'raw pack GLSL + GCC preprocessing + Mesa EGL, not Lucerna transforms/Minecraft', 'programs':[], 'failures':[]}
    active_uniform=gl.api(gl.GL,'glGetActiveUniform',None,gl.uint,gl.uint,gl.integer,C.POINTER(gl.integer),C.POINTER(gl.integer),C.POINTER(gl.enum),gl.ptr)
    with zipfile.ZipFile(pack) as archive, gl.context():
        files=sorted(n for n in archive.namelist() if re.fullmatch(r'shaders/world0/(?:deferred\d*|composite\d*|final)\.fsh',n))
        for dh in [False,True]:
            for path in files:
                stages=[];program=0
                try:
                    stages.append(gl.shader(0x8B31,preprocess(archive,path[:-4]+'.vsh',dh)))
                    stages.append(gl.shader(0x8B30,preprocess(archive,path,dh)))
                    program=gl.create_program()
                    for stage in stages: gl.attach_shader(program,stage)
                    gl.link_program(program); status=gl.integer();gl.program_status(program,0x8B82,C.byref(status))
                    if not status.value:
                        log=C.create_string_buffer(65536);gl.program_log(program,len(log),None,log);raise AssertionError(log.value.decode())
                    count=gl.integer();gl.program_status(program,0x8B86,C.byref(count));samplers={}
                    for i in range(count.value):
                        name=C.create_string_buffer(256);size=gl.integer();kind=gl.enum()
                        active_uniform(program,i,256,None,C.byref(size),C.byref(kind),name)
                        if kind.value in (0x8B5D,0x8B5E,0x8B5F,0x8B60,0x8B62):samplers[name.value.decode()]=hex(kind.value)
                    result['programs'].append({'name':PurePosixPath(path).stem,'dh':dh,'samplers':samplers})
                    print('Mellow linked:',PurePosixPath(path).stem,'DH=',dh,'samplers=',list(samplers))
                except (AssertionError,subprocess.CalledProcessError) as error:
                    result['failures'].append({'path':path,'dh':dh,'error':getattr(error,'stderr',str(error))[:1500]})
                    print('Mellow probe failure:',path,dh,str(error)[:200])
                finally:
                    if program:gl.delete_program(program)
                    for stage in stages:gl.delete_shader(stage)
    report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(result,indent=2))
    print('Mellow linked programs:',len(result['programs']),'failures:',len(result['failures']))
    if not result['programs']:raise SystemExit('No Mellow programs linked; inspect report')
if __name__=='__main__':main()
