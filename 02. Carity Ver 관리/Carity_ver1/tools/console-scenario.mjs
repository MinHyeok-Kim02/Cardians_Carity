#!/usr/bin/env node
// Pleos emulator-only demonstration. Uses the local requests observed in Console v2.1.0.
// No third-party dependencies. Node.js 18+.
import { readFileSync } from 'node:fs';
import { request as httpRequest } from 'node:http';
import { execFileSync, spawn } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const BASE = 'http://127.0.0.1:8088';
const ROOT = dirname(fileURLToPath(import.meta.url));
const GEARS = ['Park', 'Drive'];

export function validateScenario(plan) {
  if (!plan || !Array.isArray(plan.frames) || plan.frames.length < 2)
    throw new Error('Scenario needs at least two frames');
  let previous = -1;
  for (const f of plan.frames) {
    for (const key of ['at','speed','accelerator','brake','steering'])
      if (!Number.isFinite(f[key])) throw new Error('Non-finite ' + key);
    if (f.at <= previous || f.at < 0 || f.at > 120) throw new Error('Invalid frame time');
    if (f.speed < 0 || f.speed > 6 || f.accelerator < 0 || f.accelerator > 30 ||
        f.brake < 0 || f.brake > 100 || Math.abs(f.steering) > 200 || !GEARS.includes(f.gear))
      throw new Error('Frame outside demo limits');
    if (f.gear === 'Park' && (f.speed !== 0 || f.accelerator !== 0))
      throw new Error('Park frame must be stationary');
    previous = f.at;
  }
  const first = plan.frames[0], last = plan.frames.at(-1);
  if (first.at !== 0 || first.gear !== 'Park' || last.gear !== 'Park' || last.speed !== 0)
    throw new Error('Start/end must be stationary Park');
  for (let i=1;i<plan.frames.length;i++) {
    const a=plan.frames[i-1], b=plan.frames[i];
    if (a.gear !== b.gear && (a.speed !== 0 || b.speed !== 0))
      throw new Error('Gear transition must occur at zero speed');
  }
  return plan;
}

export function sample(plan, elapsed) {
  const frames=plan.frames;
  let i=0;
  while (i+1<frames.length && frames[i+1].at <= elapsed) i++;
  const a=frames[i], b=frames[Math.min(i+1,frames.length-1)];
  const t=b.at===a.at?0:Math.max(0,Math.min(1,(elapsed-a.at)/(b.at-a.at)));
  const blend=key=>Math.round(a[key]+(b[key]-a[key])*t);
  // Gear changes are discrete. Do not ramp movement while the previous gear is Park.
  return {gear:a.gear, speed:a.gear==='Park'?0:blend('speed'),
    accelerator:a.gear==='Park'?0:blend('accelerator'), brake:blend('brake'),
    steering:blend('steering')};
}

async function request(path, method='GET') {
  // The embedded HTTP server closes connections; drain bodies and avoid pooled fetch sockets.
  return new Promise((resolveRequest,reject)=>{
    const req=httpRequest(new URL(BASE+path),{method,agent:false,headers:{Connection:'close'}},res=>{
      let body='';
      res.setEncoding('utf8');
      res.on('data',chunk=>{body+=chunk;});
      res.on('error',reject);
      res.on('end',()=>{
        if(res.statusCode<200 || res.statusCode>=300)
          reject(new Error(method+' '+path+' -> HTTP '+res.statusCode+' '+body));
        else resolveRequest({text:async()=>body,json:async()=>JSON.parse(body)});
      });
    });
    req.setTimeout(2500,()=>req.destroy(new Error('Console request timed out')));
    req.on('error',reject);
    req.end();
  });
}
async function state() { return (await request('/vehicle/state')).json(); }
async function controls(f) {
  const p=new URLSearchParams({speed:String(f.speed),accelerator:String(f.accelerator),
    brake:String(f.brake),steering:String(f.steering)});
  await request('/vehicle/controls?'+p,'PUT');
}
async function gear(value) {
  await request('/vehicle/gear?gear='+encodeURIComponent(value),'PUT');
}
function verifyState(s, expected) {
  const c=s.controls;
  if(!c || s.gear?.actual?.value !== expected.gear ||
    Math.abs(Number(c.speed_kph)-expected.speed)>1 ||
    Math.abs(Number(c.steering_deg)-expected.steering)>2 ||
    Math.abs(Number(c.brake_pct)-expected.brake)>2)
    throw new Error('Console readback mismatch. Close other Console browser tabs. Actual: '+JSON.stringify({gear:s.gear,controls:c}));
}
function verifyEmulator(serial) {
  const run=args=>execFileSync('adb',['-s',serial,...args],{encoding:'utf8',timeout:6000}).trim();
  if(!/^emulator-\d+$/.test(serial)) throw new Error('Only emulator serials are accepted');
  if(run(['shell','getprop','ro.kernel.qemu']) !== '1') throw new Error('Not an emulator');
  if(!run(['emu','avd','name']).toLowerCase().includes('pleos')) throw new Error('Not a Pleos AVD');
  // Ensure this loopback port is forwarded to the selected emulator.
  execFileSync('adb',['-s',serial,'forward','tcp:8088','tcp:8088'],{timeout:6000});
}
async function probe() {
  const html=await (await request('/')).text();
  if(!html.includes('Pleos Connect Virtual Console') || !html.includes('/vehicle/controls?'))
    throw new Error('Unsupported Console page; do not guess write endpoints');
  const s=await state();
  if(!s.controls || !s.gear) throw new Error('Unsupported Console state schema');
  if(!s.gear.bridge_ready || !s.gear.actual || !s.controls.dds_ready?.all)
    throw new Error('DDS OFFLINE: Console vehicle bridge is not ready. No driving commands were sent.');
  return s;
}
async function park() {
  const stopped={gear:'Park',speed:0,accelerator:0,brake:0,steering:0};
  await controls(stopped);
  await gear('Park');
  verifyState(await state(),stopped);
}
export async function main(args) {
  const mode=args[0];
  if(!['--probe','--prepare','--run','--dry-run','--stop'].includes(mode)) {
    console.log('Usage: node tools/console-scenario.mjs --probe|--prepare|--stop');
    console.log('       node tools/console-scenario.mjs --run|--dry-run [scenario.json]');
    console.log('Run prepares Park, waits 10s for app Reset/Start, then replays and returns to Park.');
    return;
  }
  const file=args[1] || resolve(ROOT,'scenarios','left-approach.json');
  const plan=['--run','--dry-run'].includes(mode)
    ?validateScenario(JSON.parse(readFileSync(file,'utf8'))):null;
  if(mode==='--dry-run') {
    console.log('VALID '+plan.name);
    for(let t=0;t<=plan.frames.at(-1).at;t++) console.log(t,JSON.stringify(sample(plan,t)));
    return;
  }
  const serial=process.env.PLEOS_EMULATOR_SERIAL || 'emulator-5554';
  verifyEmulator(serial);
  // Console starts/stops its DDS emulator with browser-session heartbeats.
  const client='alley-scenario-'+Date.now();
  const sessionPath='/browser/session?client='+encodeURIComponent(client);
  await request(sessionPath,'POST');
  let previousAvdMode=null;
  let heartbeatError=null;
  const heartbeat=setInterval(()=>request(sessionPath,'POST').catch(error=>{heartbeatError=error;}),800);
  try {
  let connected;
  for(let attempt=0;attempt<15;attempt++) {
    try { connected=await probe(); break; }
    catch(error) { if(attempt===14) throw error; await delay(1000); }
  }
  console.log('CONNECTED',JSON.stringify({gear:connected.gear.actual.value,controls:connected.controls}));
  if(mode==='--probe') return;
  previousAvdMode=connected.hvac?.dds_mode || null;
  await request('/vehicle/hvac/mode?mode=subscribe_hvac_base','PUT');
  console.log('AVD_MODE',JSON.stringify((await state()).hvac?.dds_mode));
  await park();
  if(mode==='--prepare'||mode==='--stop') { console.log('PARK_VERIFIED'); return; }
  let sdkLog='', sdkLogError=null;
  const logger=spawn('adb',['-s',serial,'logcat','-T','1','-v','brief',
    'CarTalkSpeed:I','CarTalkBrake:I','CarTalkSteering:D','CarTalkAlley:D','*:S']);
  logger.stdout.on('data',chunk=>{sdkLog=(sdkLog+chunk.toString()).slice(-262144);});
  logger.on('error',error=>{sdkLogError=error;});
  let interrupted=false;
  const stop=()=>{interrupted=true;};
  process.on('SIGINT',stop); process.on('SIGTERM',stop);
  try {
    console.log('READY: Reset and Start the app simulation now. Starting in 10 seconds.');
    for(let n=10;n>0&&!interrupted;n--) { console.log('START_IN',n); await delay(1000); }
    if(interrupted) return;
    const start=performance.now();
    let previousGear='Park', lastRead=-1;
    const duration=plan.frames.at(-1).at;
    while(!interrupted) {
      if(heartbeatError) throw heartbeatError;
      const elapsed=(performance.now()-start)/1000;
      const f=sample(plan,Math.min(elapsed,duration));
      if(f.gear!==previousGear) {
        await controls({speed:0,accelerator:0,brake:0,steering:f.steering});
        await gear(f.gear);
        previousGear=f.gear;
      }
      await controls(f);
      const second=Math.floor(elapsed);
      if(second!==lastRead) {
        verifyState(await state(),f);
        console.log('READBACK',second,JSON.stringify(f));
        lastRead=second;
      }
      if(elapsed>=duration) break;
      await delay(100);
    }
    await delay(500);
    const observed={
      speedChanged:[...sdkLog.matchAll(/Speed = (-?[\d.]+) m\/s/g)].some(m=>Math.abs(Number(m[1]))>.1),
      steeringChanged:[...sdkLog.matchAll(/raw=(-?[\d.]+)/g)].some(m=>Math.abs(Number(m[1]))>5),
      brakePressed:sdkLog.includes('Brake = PRESSED'),
      virtualMovement:[...sdkLog.matchAll(/x=(-?[\d.]+) y=(-?[\d.]+)/g)].some(m=>Math.abs(Number(m[2])-4)>.2)
    };
    console.log('SDK_OBSERVED',JSON.stringify(observed));
    if(!interrupted && (sdkLogError || !observed.speedChanged ||
      (plan.frames.some(f=>Math.abs(f.steering)>5) && !observed.steeringChanged) ||
      (plan.frames.some(f=>f.brake>0) && !observed.brakePressed)))
      throw new Error('Console replay succeeded but app SDK reception was not verified. Keep the app in front and inspect Logcat.');
  } finally {
    try { await park(); console.log('PARK_VERIFIED'); }
    catch(error) { console.error('STOP FAILED: check emulator Console:',error.message); process.exitCode=1; }
    logger.kill();
    process.removeListener('SIGINT',stop); process.removeListener('SIGTERM',stop);
  }
  } finally {
    if(previousAvdMode) await request('/vehicle/hvac/mode?mode='+encodeURIComponent(previousAvdMode),'PUT')
      .catch(error=>console.error('AVD mode restore failed:',error.message));
    clearInterval(heartbeat);
    await request(sessionPath+'&closing=1','POST').catch(error=>console.error('Session close failed:',error.message));
  }
}
if(process.argv[1] && pathToFileURL(resolve(process.argv[1])).href===import.meta.url)
  main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1;});
