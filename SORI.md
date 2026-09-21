# Sori

Sori는 [Metrolist](https://github.com/MetrolistGroup/Metrolist)(GPL-3.0)를 포크한 앱이다. 원본 버그 수정을 계속 병합할 수 있게 변경을 최소로 유지한다.

## 원본과 다른 점

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | `applicationId` → `com.saootikim.sori`, 앱 이름 → `Sori`, 버전 규칙 (아래 참고) |
| `app/src/main/kotlin/com/metrolist/music/utils/Updater.kt` | 업데이트 확인 대상을 `saootikim/Sori`로 변경, `Sori.apk` 인식, Metrolist-KMP 업데이트 안내 끔 |
| `app/src/main/res/values{,-ko}/metrolist_strings.xml`, `values-ko/strings.xml`, `values/app_name.xml` | 화면에 보이는 "Metrolist"를 "Sori"로 변경 (크레딧은 유지) |
| `app/src/main/res/drawable*/ic_launcher_*`, `values/colors.xml`, `values/ic_launcher_background.xml` | 아이콘 (음파 막대, 보라→코랄 그라데이션) |
| `app/src/main/kotlin/com/metrolist/music/utils/LoginPagePolicy.kt` (신규), `ui/screens/LoginScreen.kt` | 로그인 후 구매 페이지 등 다른 youtube.com 페이지에 멈추는 문제 수정 (Premium 체험 대상 계정) |
| `ui/screens/settings/AboutScreen.kt`, `res/values{,-ko}/sori_strings.xml` (신규) | 소프트웨어 정보: 개발자 saootikim, "Metrolist 기반"·소스·라이선스 링크 (원작 개발자·기부·커뮤니티 링크 제거) |
| `res/drawable/small_icon.xml` | 알림바·플레이어 기본 이미지·공유 이미지 로고를 Sori 아이콘으로 |
| `sori/SoriDefaults.kt` + `App.kt` 한 줄 | Sori 기본값 시딩 (다크, 고정 브랜드 색, 클래식 플레이어, 그라데이션, 정사각 아트, 얇은 진행 막대). 사용자가 바꾼 키는 건드리지 않음 |
| `ui/theme/SoriColors.kt`, `SoriTypography.kt`, `Theme.kt` | Sori 다크 팔레트(#121212 + 코랄), 글꼴 위계. 기본 색 = Sori 코랄 |
| `ui/component/NavigationTitle.kt` | 섹션 제목 흰색, 라벨·화살표 회색 |
| `sori/SoriGreeting.kt`, `sori/ui/QuickAccessTile.kt`, `HomeScreen.kt`, `MainActivity.kt` | Home 인사말, 2열 바로가기 타일 |
| `sori/SoriHomeShelves.kt`, `sori/ui/HomeShelves.kt`, `HomeScreen.kt` | YouTube 홈 피드가 없을 때 "Sori 추천" 선반 (추천 재생목록 검색, 시간대별 순서) |
| `sori/SoriBrowse*.kt`, `sori/ui/BrowseAll.kt`, `OnlineSearchScreen.kt` | 검색 "모두 둘러보기" 컬러 타일 (YouTube 장르 → 탐색 → Sori 카탈로그 순) |
| `sori/SoriWebPlaylist.kt`, `OnlinePlaylistViewModel.kt` | 재생목록이 1곡 이하로 오면 youtube.com에서 전체 불러오기 |
| `sori/SoriAlbumFallback.kt`, `AlbumViewModel.kt` | 곡이 없는 앨범 페이지를 youtube.com 앨범 재생목록으로 채우기 |
| `sori/SoriArtistFallback.kt`, `ArtistViewModel.kt`, `artist/ArtistScreen.kt` | 노래 섹션이 없는 아티스트에 "인기곡" (youtube.com UULP 인기 동영상), 전체 재생, 라디오·셔플을 곡 라디오로 |
| `sori/SoriQueueFallback.kt`, `playback/queues/YouTubeQueue.kt` | 메뉴·카드·위젯의 재생목록 재생/셔플/라디오가 1곡이거나 실패하면 youtube.com 재생목록으로 대기열 구성 |
| `sori/SoriWebRadio.kt`, `sori/SoriYouTubeWeb.kt`, `YouTubeQueue.kt` | 막힌 곡 라디오를 youtube.com 믹스로 다시 구성하고 끝없이 이어가기. youtube.com 요청과 영상→곡 정리 공용화 |
| `sori/SongSearchFallback.kt`, `OnlineSearchViewModel.kt` | 노래 검색이 비면 비디오 검색으로 대체 |
| `sori/VideoTitleCleaner.kt`, `sori/VideoArtistTitle.kt` | 영상 제목 정리, "아티스트 - 제목" 분리, 채널명 정리 |
| `sori/SearchSectionTitle.kt`, `OnlineSearchResult.kt` | 검색 결과 그룹 제목 한국어화 |
| `sori/ui/CoverGradient.kt`, `OnlinePlaylistScreen.kt`, `AlbumScreen.kt` | 재생목록·앨범 머리와 상단 바를 표지 색으로 물들이기 (바와 머리가 이어지게) |
| `sori/ui/LikedArt.kt`, `ui/component/Items.kt` | 보관함 "좋아요 표시됨" 표지를 보라→코랄 그라데이션 + 흰 하트로 |
| `MainActivity.kt`, `ui/component/AppNavigation.kt` | 상단 바는 페이지 배경과 같은 색(스크롤 시에만 색), 하단 탐색 바는 거의 검정 |
| `library/LibraryMixScreen.kt`, `library/LibraryPlaylistsScreen.kt`, `ui/component/HideOnScrollFAB.kt` | FAB를 코랄 원형으로 (화면의 주요 동작) |
| `sori/ThumbnailLetterbox.kt`, `App.kt` | YouTube hqdefault/sddefault 썸네일에 박힌 검은 띠를 Coil 인터셉터로 잘라내기 (목록·플레이어·알림 전부) |
| `ui/player/Player.kt`, `ui/player/Queue.kt` | 공유·메뉴 버튼은 반투명, 재생 버튼만 흰색. 수면 타이머 라벨을 "타이머"로 줄임 (길어서 흘러가던 문제) |
| `sori/ui/KeepAtTop.kt`, `HomeScreen.kt` | 바로가기는 항상 Home 맨 위 (원본은 섹션 순서를 섞음), 섹션이 늦게 와도 Home이 맨 위에서 열림, Home FAB 제거 (셔플 타일과 중복) |
| `res/values-ko/sori_translations.xml` (신규) | 원본에 한국어가 없는 문자열 번역. 원본이 같은 번역을 추가하면 중복 리소스로 빌드가 실패하니 여기서 지운다 |
| `.github/workflows/release.yml` | GitHub 기본 러너 사용, FOSS 빌드만, 결과물 `Sori.apk` |
| `.gitignore` | `*.jks`, `*.keystore` 제외 |

Kotlin 패키지(`com.metrolist.music`)는 그대로 둔다. 바꾸면 원본을 병합할 수 없게 된다.

## 버전 규칙

- `versionName`: Sori 자체 번호. 버그 수정은 `1.0.1`, 기능 추가는 `1.1.0` 식으로 올린다.
- `versionCode`: 릴리스마다 1씩 올린다 (1.0.0 = `15303`). 이전 릴리스보다 작으면 덮어쓰기 업데이트가 안 된다.
- 원본을 병합할 때 원본의 `versionCode`와 `versionName` 줄은 버리고 Sori 값을 유지한다.
- 앱은 릴리스 제목(= versionName)이 자기 버전보다 크면 업데이트 알림을 띄운다.
- 13.7.0.1 / 13.7.0.2 설치본은 1.0.0을 새 버전으로 인식하지 못한다. 그래서 한 번은 링크(`https://github.com/saootikim/Sori/releases/latest/download/Sori.apk`)로 직접 받아서 덮어쓰기 설치해야 한다. 로그인과 데이터는 유지된다.

## 릴리스 방법

1. `app/build.gradle.kts`의 `versionName`과 `versionCode`를 올린다.
2. `main` 브랜치에 push한다.
3. `release.yml`이 버전 변경을 감지하고 서명된 `Sori.apk`를 만들어 GitHub Release로 올린다.
4. 친구들 앱이 최대 2시간 안에 새 릴리스를 확인하고 업데이트 알림을 띄운다.

수동으로 실행하려면: `gh workflow run release.yml -R saootikim/Sori`

이 포크에서는 push로 워크플로가 자동 실행되지 않은 적이 있다. 버전을 올려 push했는데 Actions 탭에 실행 기록이 없으면 위 명령으로 직접 실행한다.

## 원본 동기화 (스트림이 안 나오는 등 원본이 수정했을 때)

```bash
git fetch upstream
git merge upstream/main
# 충돌은 위 표에 있는 파일에서만 난다. Sori 쪽 변경을 유지하면서 해결한다.
# 원본 versionName이 바뀌었으면 위 버전 규칙대로 다시 맞춘다.
git push origin main
```

## 서명 키

- 릴리스 APK는 GitHub Secrets에 등록한 키로 서명된다: `KEYSTORE`(base64), `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`
- **`sori-release.jks`를 잃어버리면 친구들이 앱을 업데이트할 수 없다.** 앱을 지우고 새로 설치해야 하고, 그러면 다운로드한 곡과 설정이 사라진다. 저장소 바깥 두 군데 이상에 백업해 둘 것.

## 원본 파일 수정 원칙

- Sori 코드는 `com.metrolist.music.sori` 패키지와 `ui/theme/Sori*.kt` 새 파일에 둔다.
- 원본 파일에는 호출 한두 줄만 넣고 `// Sori:` 주석을 단다. 원본을 병합할 때 충돌이 나면 이 주석을 찾으면 된다.
- 기본값은 원본의 `defaultValue`를 고치지 않고 `SoriDefaults`로 시딩한다. 기본값을 추가하려면 `VERSION`을 올린다.

## 로컬 빌드

JDK 21이 필요하다 (`jvmToolchain(21)`). 사용자 폴더처럼 한글이 들어간 경로는 AGP가 빌드를 막기 때문에, 저장소는 ASCII 경로(`C:\dev\sori`)에 둔다.

단위 테스트는 임시 폴더 경로에 한글이 있으면 Robolectric SQLite가 JVM째 죽는다. `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/dev/tmp`로 돌린다.

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot" ./gradlew :app:assembleFossDebug
```
