# Contador de Trocas — Android Kotlin

App Android **nativo** em Kotlin + Jetpack Compose para controlar pessoas que trocaram notas de R$ 5 e R$ 10.

## Cadastro e regras
- Informe **nome, data e valor total trocado em reais**, diretamente. **Não é necessário informar a quantidade de notas.**
- Se desejar, indique apenas o tipo de nota com um toque em **R$ 5** ou **R$ 10**; ou deixe **Não informar**. O valor total é independente disso.
- O valor total é digitado diretamente, sem cálculo obrigatório por notas.
- **Não existe quantidade mínima de cada tipo de nota:** só notas de R$ 5, só notas de R$ 10, ou misturadas são aceitas. O critério para presente é exclusivamente o **valor mensal total ≥ R$ 150,00**.
- **A cada novo mês o painel, ranking e elegibilidade começam zerados automaticamente.** Nada do mês anterior é somado à campanha atual. Dentro do mesmo mês, as trocas de uma pessoa somam para atingir R$ 150.
- **Histórico:** os registros e sorteios dos meses encerrados continuam disponíveis para consulta (incluindo totais de cada mês e total histórico), sem entrar nos cálculos do mês atual.
- **R$ 150,00 exatos já tornam a pessoa elegível:** total mensal **≥ R$ 150,00**.
- Ranking mensal, lista de elegíveis, sorteio com uma tentativa por pessoa por mês, histórico de sorteios e confirmação de entrega.
- Presentes iniciais: caixa de bombom, trufa, bolo de pote, desodorante, kit sabonete e escova de dente. Cadastro, edição e exclusão.
- Backup JSON exportável e importável pelas ferramentas de arquivos do Android.

- Na aba **Registrar**, use **Editar** para corrigir nome, data, valor total e tipo de nota. O ranking recalcula automaticamente. Se houve sorteio, um aviso aparece e o sorteio antigo é preservado. A exclusão segue disponível com confirmação.

## Executar
1. Abra o repositório no **Android Studio**.
2. Sincronize o Gradle (JDK 17 ou superior, SDK Android 36). Versão alvo Gradle 8.14.3.
3. Execute o módulo `app` em um dispositivo Android 8+ ou emulador.

No GitHub, o workflow **Android CI** faz testes e tenta gerar o APK em **Actions → Android CI → Artifacts → ContadorTroco-debug-apk**. Ele ficará disponível se o build passar. Não é necessário Railway.

Os registros são **locais**, sem login nem sincronização entre aparelhos. Exporte backups com frequência. O backup inclui nomes e valores, não o publique em repositórios públicos.

## Assinatura permanente e atualizacoes
- APKs **debug** do GitHub Actions usam chaves temporarias do runner e podem ter assinatura diferente a cada compilacao.
- Para distribuir APKs atualizaveis, use uma unica chave privada de assinatura `.jks` guardada **fora** deste repositorio e cadastre no GitHub Actions (Settings -> Secrets and variables -> Actions) os quatro secrets:
  - `ANDROID_KEYSTORE_BASE64`: conteudo base64 do arquivo keystore
  - `ANDROID_KEYSTORE_PASSWORD`: senha do keystore
  - `ANDROID_KEY_ALIAS`: nome/alias da chave
  - `ANDROID_KEY_PASSWORD`: senha da chave privada
- Depois, em cada push para `main`, o workflow gera o artifact **ContadorTroco-assinado-release-apk**, com o mesmo applicationId, assinatura fixa e versionCode crescente.
- Sem secrets, o workflow continua compilando somente o APK de debug. A chave **nunca** e colocada no codigo-fonte ou em comentarios de PR.
- Quem instalou um APK debug com assinatura diferente precisara **exportar backup JSON, desinstalar a versao antiga, instalar a release assinada e restaurar JSON uma unica vez**. A partir dai, a atualizacao e por cima, sem remover o app.
- **Guarde a chave .jks e senhas em dois locais privados e confiaveis; perder a chave impede novas atualizacoes assinadas com ela.**
