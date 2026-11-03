import test from 'node:test'
import assert from 'node:assert/strict'
import {equipmentSegments} from '../src/equipment-segments.js'

test('known equipment names are linked wherever mentioned',()=>assert.deepEqual(equipmentSegments('建议便携微单；便携微单适合入门。',[{id:5,name:'便携微单'}]),[{text:'建议'},{text:'便携微单',to:'/equipment/5'},{text:'；'},{text:'便携微单',to:'/equipment/5'},{text:'适合入门。'}]))
test('prefer full longer model names when names overlap',()=>assert.deepEqual(equipmentSegments('便携微单 · 旅行套机',[{id:1,name:'便携微单'},{id:5,name:'便携微单 · 旅行套机'}]),[{text:'便携微单 · 旅行套机',to:'/equipment/5'}]))
test('unknown equipment and model supplied links are not trusted',()=>assert.deepEqual(equipmentSegments('[虚构型号](javascript:alert(1)) <script>bad</script>',[]),[{text:'[虚构型号](javascript:alert(1)) <script>bad</script>'}]))
test('invalid ids, whitespace names and ambiguous names are not linked',()=>assert.deepEqual(equipmentSegments('相机 镜头 灯光',[{id:-1,name:'相机'},{id:2,name:'镜头'},{id:3,name:'镜头'},{id:4,name:' '},{id:Infinity,name:'灯光'}]),[{text:'相机 镜头 灯光'}]))
test('untrusted reference URLs are ignored and only internal routes generated',()=>assert.deepEqual(equipmentSegments('相机',[{id:1,name:'相机',url:'https://evil.example'}]),[{text:'相机',to:'/equipment/1'}]))
test('old history without link metadata stays readable',()=>assert.deepEqual(equipmentSegments('旧回复',null),[{text:'旧回复'}]))
test('paired emphasis around verified names is replaced by styled links',()=>assert.deepEqual(equipmentSegments('推荐 **便携微单**，也可看__镜头__。',[{id:5,name:'便携微单'},{id:6,name:'镜头'}]),[{text:'推荐 '},{text:'便携微单',to:'/equipment/5'},{text:'，也可看'},{text:'镜头',to:'/equipment/6'},{text:'。'}]))
