/* Caminho inverso: abre com o cripto-casa.js REAL do PWA o que o Kotlin cifrou no InteropTest
   (dominio/build/interop-kotlin.json, gerado por "gradle :dominio:test"). Rodar: node scripts/conferir-interop.mjs */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');
const C = await import(join(raiz, '..', 'stk-desp-firebase-google', 'src', 'js', 'cripto-casa.js'));
const k = JSON.parse(readFileSync(join(raiz, 'dominio', 'build', 'interop-kotlin.json'), 'utf8'));

assert.equal(await C.hashDaEntrada(k.palavras.join(' ')), k.hash, 'hash das palavras');
assert.equal(await C.seloDe(k.casaId, k.hash), k.cripto.selo, 'selo');
assert.equal(await C.kidDe(k.dek), k.cripto.kid, 'kid');
assert.equal(await C.abrirComPalavras(k.casaId, k.cripto, k.palavras.join(' ')), k.dek, 'abrir com palavras');
assert.equal(await C.abrirComCodigo(k.casaId, k.cripto, k.codigo), k.dek, 'abrir com código');
const cert = await C.abrirArquivoCertificado(k.envelope.arquivo, k.envelope.senha);
assert.equal(cert.hash, k.envelope.cripto.cert, 'hash do certificado');
assert.equal(await C.abrirComCertificado(k.casaId, k.envelope.cripto, cert.cert), k.dek, 'abrir com certificado');
assert.equal(await C.abrirComCodigo(k.casaId, k.envelope.cripto, k.envelope.codigo), k.dek, 'código novo do envelope');
const valor = await C.abrir(await C.chaveDados(k.dek), k.registro.caixa, k.registro.contexto);
assert.deepEqual(valor, k.registro.valor, 'registro cifrado pelo Kotlin');
console.log('OK: o PWA abriu tudo o que o Kotlin cifrou (palavras, código, certificado e registro).');
