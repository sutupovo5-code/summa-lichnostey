const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,runTransaction,updateDoc}=require('firebase/firestore');
const fs=require('fs');
(async()=>{const env=await initializeTestEnvironment({projectId:'demo-summa',firestore:{host:'127.0.0.1',port:8085,rules:fs.readFileSync(require('path').join(__dirname,'../firestore.rules'),'utf8')}});let checks=0;await env.clearFirestore();
const initial={profile:{name:'Tester'},st:{sp:1000,ts:0,jw:0},cards:{ch:{one:2},sk:{},log:[]},shop:{},eggs:[],v:1,updatedAt:1};
await env.withSecurityRulesDisabled(async c=>{for(const id of ['alice','bob'])await setDoc(doc(c.firestore(),'users',id),initial);await setDoc(doc(c.firestore(),'posts','fund'),{ownerUid:'alice',post:{fundGoal:500},del:false})});
const db=id=>env.authenticatedContext(id,{firebase:{sign_in_provider:'password'}}).firestore();const a=db('alice'),b=db('bob'),c=db('mallory');
const trade={from:'alice',to:'bob',giveMoney:200,giveCard:'ch:one',wantMoney:100,wantCard:'',status:'pending',claimed:false,at:1};
await assertFails(setDoc(doc(a,'exchanges','bad'),trade));checks++;
await assertSucceeds(runTransaction(a,async t=>{const u=doc(a,'users','alice'),s=await t.get(u),d=s.data();d.st.sp-=200;d.cards.ch.one--;d.assetOp='exchanges/ok:create';t.set(u,d);t.set(doc(a,'exchanges','ok'),trade)}));checks++;
await assertFails(getDoc(doc(c,'exchanges','ok')));checks++;
await assertFails(updateDoc(doc(b,'exchanges','ok'),{status:'accepted'}));checks++;
await assertSucceeds(runTransaction(b,async t=>{const u=doc(b,'users','bob'),s=await t.get(u),d=s.data();d.st.sp+=100;d.cards.ch.one++;d.assetOp='exchanges/ok:accept';t.set(u,d);t.update(doc(b,'exchanges','ok'),{status:'accepted'})}));checks++;
await assertFails(updateDoc(doc(b,'exchanges','ok'),{status:'pending'}));checks++;
await assertSucceeds(runTransaction(a,async t=>{const u=doc(a,'users','alice'),s=await t.get(u),d=s.data();d.st.sp+=100;d.assetOp='exchanges/ok:claim';t.set(u,d);t.update(doc(a,'exchanges','ok'),{claimed:true})}));checks++;
await assertFails(updateDoc(doc(a,'exchanges','ok'),{claimed:false}));checks++;
const fund={owner:'alice',goal:500,raised:100,paid:0,lastBy:'bob',lastAmount:100,nonce:'one'};
await assertFails(setDoc(doc(b,'fundraisers','fund'),fund));checks++;
await assertSucceeds(runTransaction(b,async t=>{const u=doc(b,'users','bob'),s=await t.get(u),d=s.data();d.st.sp-=100;d.assetOp='fundraisers/fund:one';t.set(u,d);t.set(doc(b,'fundraisers','fund'),fund);t.set(doc(b,'fundraisers','fund','contributors','bob'),{name:'Tester',total:100,nonce:'one',at:1})}));checks++;
await assertSucceeds(runTransaction(a,async t=>{const u=doc(a,'users','alice'),s=await t.get(u),d=s.data();d.st.sp+=100;d.assetOp='fundraisers/fund:two';t.set(u,d);t.update(doc(a,'fundraisers','fund'),{paid:100,lastBy:'alice',lastAmount:0,nonce:'two'})}));checks++;
await assertFails(updateDoc(doc(a,'fundraisers','fund'),{paid:100,lastBy:'alice',lastAmount:0,nonce:'two'}));checks++;
// A gift can be cancelled exactly once and reserved assets return to its sender.
await assertSucceeds(runTransaction(a,async t=>{const u=doc(a,'users','alice'),q=await t.get(u),d=q.data();d.st.sp-=200;d.cards.ch.one--;d.assetOp='exchanges/cancel:create';t.set(u,d);t.set(doc(a,'exchanges','cancel'),trade)}));checks++;
await assertSucceeds(runTransaction(a,async t=>{const u=doc(a,'users','alice'),q=await t.get(u),d=q.data();d.st.sp+=200;d.cards.ch.one++;d.assetOp='exchanges/cancel:cancel';t.set(u,d);t.update(doc(a,'exchanges','cancel'),{status:'cancelled'})}));checks++;
// Two reservations cannot be paid for with the same account deduction.
await assertFails(runTransaction(a,async t=>{const u=doc(a,'users','alice'),q=await t.get(u),d=q.data();d.st.sp-=200;d.cards.ch.one--;d.assetOp='exchanges/batchA:create';t.set(u,d);t.set(doc(a,'exchanges','batchA'),trade);t.set(doc(a,'exchanges','batchB'),trade)}));checks++;
await assertFails(updateDoc(doc(b,'fundraisers','fund','contributors','bob'),{total:999}));checks++;
// Repeated support is aggregated per donor and cannot be inflated independently.
await assertSucceeds(runTransaction(b,async t=>{const u=doc(b,'users','bob'),q=await t.get(u),d=q.data();d.st.sp-=50;d.assetOp='fundraisers/fund:three';t.set(u,d);t.update(doc(b,'fundraisers','fund'),{raised:150,lastBy:'bob',lastAmount:50,nonce:'three'});t.update(doc(b,'fundraisers','fund','contributors','bob'),{name:'Tester',total:150,nonce:'three',at:2})}));checks++;
await assertFails(updateDoc(doc(b,'fundraisers','fund','contributors','bob'),{name:'Someone else'}));checks++;
console.log('Rules checks passed:',checks);await env.cleanup();process.exit(0)})().catch(e=>{console.error(e);process.exit(1)});
