import test from 'node:test';
import assert from 'node:assert/strict';
import {validateScenario,sample} from './console-scenario.mjs';
import {readFileSync} from 'node:fs';
const plan=JSON.parse(readFileSync(new URL('./scenarios/left-approach.json',import.meta.url),'utf8'));
test('valid demo starts/ends parked and interpolates movement',()=>{
    validateScenario(plan);
    assert.equal(sample(plan,1).speed,0);
    assert.equal(sample(plan,3.5).speed,2);
    assert.equal(sample(plan,22).gear,'Park');
    assert.equal(sample(plan,22).speed,0);
});
test('reject moving Park, excessive speed, NaN and moving gear changes',()=>{
    for(const mutate of [
        p=>p.frames[0].speed=2,
        p=>p.frames[2].speed=100,
        p=>p.frames[2].steering=NaN,
        p=>p.frames[1].speed=1
    ]) { const copy=structuredClone(plan); mutate(copy); assert.throws(()=>validateScenario(copy)); }
});
