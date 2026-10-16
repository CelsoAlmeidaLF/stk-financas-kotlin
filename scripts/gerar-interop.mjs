/* Gera dominio/src/test/resources/interop-js.json com o cripto-casa.js REAL do PWA.
   O InteropTest (Kotlin) abre tudo isto: prova que o app Kotlin lê o que o PWA gravou.
   Segredos aqui são de teste, aleatórios e descartáveis. Rodar: node scripts/gerar-interop.mjs */
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');
const C = await import(join(raiz, '..', 'stk-desp-firebase-google', 'src', 'js', 'cripto-casa.js'));

const casaId = 'casaInterop01';
const p = await C.novasPalavras(casaId);
const { dek, cripto, codigo } = await C.criarCriptoCasa(casaId, p);
const senhaCert = 'senha-ção-forte'; // não ASCII de propósito: confere o PBKDF2 em UTF-8
const env = await C.novoEnvelope(casaId, cripto, dek, { senha: senhaCert, casaNome: 'Casa Interop' });

const k = await C.chaveDados(dek);
const valor = { tipo: 'despesa', descricao: 'Pão de açúcar', valorCentavos: 4590, data: '2026-10-07', categoria: 'Mercado', autorNome: 'Ana' };
const contexto = C.aad(casaId, 'lancamentos', 'doc01');
const caixa = await C.selar(k, valor, contexto);

const vetores = [];
for (const hex of ['00000000000000000000000000000000', 'ffffffffffffffffffffffffffffffff', '0123456789abcdeffedcba9876543210']) {
  const e = Uint8Array.from(hex.match(/../g), b => parseInt(b, 16));
  const hash = await C.hashDaEntropia(e);
  vetores.push({ entropiaHex: hex, palavras: await C.palavrasDaEntropia(e), hash, selo: await C.seloDe(casaId, hash) });
}

const saida = {
  casaId, palavras: p.palavras, hash: p.hash, selo: p.selo, codigo, dek, kid: await C.kidDe(dek), cripto,
  envelope: { senha: senhaCert, arquivo: env.arquivo, codigo: env.codigo, hash: env.hash, cripto: env.cripto },
  registro: { contexto, caixa, valor },
  vetores
};
const arq = join(raiz, 'dominio', 'src', 'test', 'resources', 'interop-js.json');
writeFileSync(arq, JSON.stringify(saida, null, 2) + '\n');
console.log('gerado', arq);
