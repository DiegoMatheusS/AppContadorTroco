# Contador de Trocas

Aplicativo simples, gratuito e sem servidor para registrar trocas de dinheiro em notas de **R$ 5 e R$ 10** e sortear presentes.

## Como funciona

- Registre **nome, data, valor e notas** utilizadas em cada troca. Para notas de R$ 10, o valor deve ser múltiplo de R$ 10; para notas de R$ 5 ou mistas, múltiplo de R$ 5.
- O app soma todas as trocas de uma pessoa **dentro do mesmo mês**, sem diferenciar maiúsculas/minúsculas e acentos no nome.
- Quem ultrapassar **R$ 150,00** (R$ 150,00 exatos não bastam) fica elegível para **um sorteio de presente naquele mês**.
- Toque na pessoa elegível no ranking para fazer o sorteio. O resultado é salvo, não sendo possível sortear de novo para a mesma pessoa e mês.
- Cadastre, remova e altere os tipos de presentes disponíveis. Os sorteios antigos continuam guardados se algum presente for removido.
- Marque o presente como entregue e consulte meses anteriores.
- Exporte ou importe um arquivo **JSON** de backup nas Configurações.

## Publicar grátis no GitHub Pages

1. Integre a PR à branch `main`.
2. Abra **Settings → Pages → Build and deployment**.
3. Em **Source**, selecione **Deploy from a branch**.
4. Selecione **main**, pasta **/ (root)**, e salve.
5. Abra a URL publicada em um navegador no celular e use **Adicionar à tela inicial** (instalabilidade varia conforme o navegador).

Não é necessário Railway, Node ou banco de dados para utilizar. Os dados são armazenados **somente no navegador e dispositivo em uso**, por meio de `localStorage`. Não há sincronização automática entre celulares, nem entre navegadores; limpar os dados do navegador pode apagar os registros. **Exporte backups frequentemente** e guarde-os em local seguro. O aplicativo pode ser usado offline após a primeira visita, quando servido via HTTPS.

## Desenvolvimento

Abrir `index.html` no navegador para testar o básico (para testar PWA/service worker, sirva por localhost ou HTTPS).

```sh
node --test tests/*.test.cjs
node --check app.js
```

## Privacidade

Sem login, rastreadores, anúncios ou serviços externos. Nomes e registros permanecem no dispositivo, salvo quando o usuário exporta o backup. Não publique arquivos de backup com nomes de participantes em repositórios públicos.
