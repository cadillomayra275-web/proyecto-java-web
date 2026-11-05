import {spawn} from 'node:child_process';
import fs from 'node:fs/promises';
const puerto=18080;
const archivo=await fs.open('pruebas/servidor.log','w');
const servidor=spawn('java',['-cp',`bin/siscom.jar${process.platform==='win32'?';':':'}lib/*`,'pe.edu.siscom.Inicio'],{env:{...process.env,SISCOM_PUERTO:String(puerto),SISCOM_DB_URL:'jdbc:h2:./pruebas/datos/siscom;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000',SISCOM_PRUEBAS_FALLO:'true'},stdio:['ignore',archivo.fd,archivo.fd]});
try{
 let listo=false;
 for(let i=0;i<60;i++){
  try{const r=await fetch(`http://127.0.0.1:${puerto}/api/health`);if(r.ok){listo=true;break;}}catch{}
  await new Promise(resolver=>setTimeout(resolver,500));
 }
 if(!listo)throw new Error('El servidor de pruebas no inicio Revise pruebas/servidor.log');
 process.argv[2]=`http://127.0.0.1:${puerto}/api`;
 await import('./verificar.mjs');
}finally{servidor.kill('SIGTERM');await archivo.close();}
