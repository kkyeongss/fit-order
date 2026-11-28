"""제출용 기획서(Word)를 만든다. 안내문(2026년도 공공데이터 활용 경진대회) 양식: 기획 요약서 1장 + 상세 기획서 10장 이내.

사용: python 기획서_생성.py images 핏오더_기획서.docx [미리보기.html]   (python-docx, Pillow 필요)
미리보기.html: Word 서식(글꼴·줄 간격·표 여백)을 흉내 낸 HTML. 쪽수와 모양을 확인하는 용도(실제 쪽수는 Word에서 확인).
이미지폴더(images/)에는 dashboard.png, recommend-top.png, recommend-detail.png, sizes.png, admin-prices.png 가 있다
(시연 서버 화면을 캡처해 자른 것). 수치는 시연 서버(https://fitorder.duckdns.org)의 실제 값이다.
"""
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

FONT = '맑은 고딕'
NAVY = RGBColor(0x1F, 0x3A, 0x5F)
GRAY = RGBColor(0x55, 0x60, 0x70)
IMG = Path(sys.argv[1])
OUT = Path(sys.argv[2])
PREVIEW = Path(sys.argv[3]) if len(sys.argv) > 3 else None
HTML = []   # 미리보기용
# 맑은 고딕의 '한 줄' 높이는 글자 크기의 약 1.33배. 본문 줄 간격 1.12배를 곱한다.
LINE = 1.12 * 1.33
K = 1.07   # 글자 크기 배율(읽기 쉽게 약간 키움)


def fs(size):
    return round(size * K * 2) / 2


def esc(t):
    import html as _h
    t = _h.escape(t)
    parts = t.split('**')
    return ''.join(('<b>%s</b>' % x) if i % 2 else x for i, x in enumerate(parts)).replace('\n', '<br>')


def hexc(c):
    return '#%02x%02x%02x' % (c[0], c[1], c[2]) if c is not None else '#000'

doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21.0), Cm(29.7)
sec.left_margin = sec.right_margin = Cm(1.9)
sec.top_margin, sec.bottom_margin = Cm(1.7), Cm(1.6)
CONTENT_W = 21.0 - 1.9 * 2  # cm


def set_font(style_or_run, size=None, bold=None, color=None):
    f = style_or_run.font
    f.name = FONT
    el = style_or_run.element if hasattr(style_or_run, 'element') else style_or_run._element
    rpr = el.get_or_add_rPr() if hasattr(el, 'get_or_add_rPr') else el.rPr
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    for attr in ('w:eastAsia', 'w:ascii', 'w:hAnsi'):
        rfonts.set(qn(attr), FONT)
    if size:
        f.size = Pt(size)
    if bold is not None:
        f.bold = bold
    if color is not None:
        f.color.rgb = color


normal = doc.styles['Normal']
set_font(normal, 9.5)
normal.paragraph_format.space_after = Pt(2)
normal.paragraph_format.space_before = Pt(0)
normal.paragraph_format.line_spacing = 1.12


def para(text='', size=9.5, bold=False, color=None, align=None, after=2, before=0, indent=None, keep=False):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.space_before = Pt(before)
    if indent is not None:
        p.paragraph_format.left_indent = Cm(indent)
    if align:
        p.alignment = align
    if keep:
        p.paragraph_format.keep_with_next = True
    add_runs(p, text, size, bold, color)
    st = f'font-size:{fs(size)}pt;margin:{before}pt 0 {after}pt {indent or 0}cm;'
    st += 'font-weight:bold;' if bold else ''
    st += f'color:{hexc(color)};' if color is not None else ''
    st += 'text-align:center;' if align == WD_ALIGN_PARAGRAPH.CENTER else ''
    HTML.append(f'<p class="p" style="{st}">{esc(text)}</p>')
    return p


def add_runs(p, text, size=9.5, bold=False, color=None):
    """**굵게** 표시를 지원한다."""
    parts = text.split('**')
    for i, part in enumerate(parts):
        if not part:
            continue
        r = p.add_run(part)
        set_font(r, fs(size), bold or i % 2 == 1, color)


def h1(text):
    p = para(text, 13, True, NAVY, before=8, after=3, keep=True)
    border_bottom(p)
    HTML[-1] = HTML[-1].replace('class="p"', 'class="p h1"')
    return p


def h2(text):
    return para(text, 10.5, True, NAVY, before=5, after=2, keep=True)


def bullet(text, size=9.5, level=0, after=1.5):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5 + 0.5 * level)
    p.paragraph_format.first_line_indent = Cm(-0.35)
    p.paragraph_format.space_after = Pt(after)
    add_runs(p, ('· ' if level == 0 else '- ') + text, size)
    HTML.append(f'<p class="p" style="font-size:{fs(size)}pt;margin:0 0 {after}pt {0.5 + 0.5 * level}cm;text-indent:-0.35cm">{"· " if level == 0 else "- "}{esc(text)}</p>')
    return p


def border_bottom(p):
    ppr = p._p.get_or_add_pPr()
    b = OxmlElement('w:pBdr')
    bt = OxmlElement('w:bottom')
    bt.set(qn('w:val'), 'single')
    bt.set(qn('w:sz'), '6')
    bt.set(qn('w:space'), '1')
    bt.set(qn('w:color'), '1F3A5F')
    b.append(bt)
    ppr.append(b)


def shade(cell, hex_color):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), hex_color)
    tcpr.append(shd)


def cell_margins(table, top=40, bottom=40, left=70, right=70):
    tblpr = table._tbl.tblPr
    m = OxmlElement('w:tblCellMar')
    for k, v in (('top', top), ('left', left), ('bottom', bottom), ('right', right)):
        e = OxmlElement(f'w:{k}')
        e.set(qn('w:w'), str(v))
        e.set(qn('w:type'), 'dxa')
        m.append(e)
    tblpr.append(m)


def table(rows, widths, header=True, size=8.5, bold_first_col=False, align_right_cols=(), head_fill='DCE4EF'):
    t = doc.add_table(rows=len(rows), cols=len(widths))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    cell_margins(t)
    for ri, row in enumerate(rows):
        for ci, text in enumerate(row):
            cell = t.cell(ri, ci)
            cell.width = Cm(widths[ci])
            cell.text = ''
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            lines = str(text).split('\n')
            for li, line in enumerate(lines):
                if li > 0:
                    p = cell.add_paragraph()
                    p.paragraph_format.space_after = Pt(0)
                is_head = header and ri == 0
                add_runs(p, line, size, bold=is_head or (bold_first_col and ci == 0))
                if ci in align_right_cols and not is_head:
                    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
                if is_head:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            if header and ri == 0:
                shade(cell, head_fill)
            elif bold_first_col and ci == 0:
                shade(cell, 'F1F4F8')
    cols = ''.join(f'<col style="width:{w}cm">' for w in widths)
    body = ''
    for ri, row in enumerate(rows):
        body += '<tr>'
        for ci, text in enumerate(row):
            head = header and ri == 0
            fill = '#DCE4EF' if head else ('#F1F4F8' if bold_first_col and ci == 0 else '')
            st = f'font-size:{fs(size)}pt;' + (f'background:{fill};' if fill else '')
            st += 'font-weight:bold;text-align:center;' if head else ('font-weight:bold;' if bold_first_col and ci == 0 else '')
            st += 'text-align:right;' if ci in align_right_cols and not head else ''
            body += f'<td style="{st}">{esc(str(text))}</td>'
        body += '</tr>'
    HTML.append(f'<table class="t"><colgroup>{cols}</colgroup>{body}</table><div style="height:4pt"></div>')
    # 표 뒤 간격
    sp = doc.add_paragraph()
    sp.paragraph_format.space_after = Pt(2)
    sp.paragraph_format.line_spacing = 0.5
    return t


def picture(name, width_cm, caption=None):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(1)
    p.paragraph_format.keep_with_next = bool(caption)
    p.add_run().add_picture(str(IMG / name), width=Cm(width_cm))
    import base64
    b64 = base64.b64encode((IMG / name).read_bytes()).decode()
    HTML.append(f'<div style="text-align:center;margin:0 0 1pt"><img src="data:image/png;base64,{b64}" style="width:{width_cm}cm"></div>')
    if caption:
        c = para(caption, 8, False, GRAY, WD_ALIGN_PARAGRAPH.CENTER, after=4)
        return c


def footer_page_number():
    p = sec.footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run('핏오더(FitOrder) 기획서  -  ')
    set_font(r, 8, False, GRAY)
    r2 = p.add_run()
    set_font(r2, 8, False, GRAY)
    for tag, text in (('begin', None), (None, ' PAGE '), ('end', None)):
        if tag:
            fc = OxmlElement('w:fldChar')
            fc.set(qn('w:fldCharType'), tag)
            r2._r.append(fc)
        else:
            it = OxmlElement('w:instrText')
            it.set(qn('xml:space'), 'preserve')
            it.text = text
            r2._r.append(it)


footer_page_number()

# ============================================================================================
# 기획 요약서 (1장)
# ============================================================================================
para('2026년도 공공데이터 활용 경진대회', 10, False, GRAY, WD_ALIGN_PARAGRAPH.CENTER, after=0)
para('기 획 요 약 서', 18, True, NAVY, WD_ALIGN_PARAGRAPH.CENTER, after=6)

def stat_boxes(items):
    t = doc.add_table(rows=2, cols=len(items))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    cell_margins(t, 50, 50, 70, 70)
    w = CONTENT_W / len(items)
    html = ''
    for i, (big, label) in enumerate(items):
        top, bottom = t.cell(0, i), t.cell(1, i)
        for c in (top, bottom):
            c.width = Cm(w)
            shade(c, 'EAF0F7')
            c.text = ''
            c.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
            c.paragraphs[0].paragraph_format.space_after = Pt(0)
        add_runs(top.paragraphs[0], big, 14, True, NAVY)
        add_runs(bottom.paragraphs[0], label, 8, False, GRAY)
    cells_top = ''.join(f'<td style="background:#EAF0F7;text-align:center;font-weight:bold;color:#1F3A5F;font-size:{fs(14)}pt">{esc(b)}</td>' for b, _ in items)
    cells_bot = ''.join(f'<td style="background:#EAF0F7;text-align:center;color:#556070;font-size:{fs(8)}pt">{esc(l)}</td>' for _, l in items)
    HTML.append(f'<table class="t"><tr>{cells_top}</tr><tr>{cells_bot}</tr></table><div style="height:4pt"></div>')
    sp = doc.add_paragraph()
    sp.paragraph_format.space_after = Pt(2)
    sp.paragraph_format.line_spacing = 0.5


stat_boxes([
    ('1,402벌', '연평균 주문량 (2020~2025)'),
    ('16.7%', '사이즈 미스매치율'),
    ('약 442만원', '예상 절감액 / 연 (추정)'),
    ('2.17 tCO₂e', '예상 탄소 감축 / 연 (추정)'),
])

h2('1. 활용사례명')
para('**핏오더(FitOrder)** — 공공 근무복 주문 데이터로 사이즈별 발주량을 추천하고, 과잉 발주를 줄인 효과를 ESG 지표(절감액·탄소)로 보여주는 서비스', 9.5, after=3)

h2('2. 사용한 공공데이터명')
table([
    ['구분', '공공데이터명 (공공데이터포털)', '형태'],
    ['한국체육산업개발(주)\n개방 데이터 3종', '한국체육산업개발주식회사_근무복 주문현황 (15168288)\n한국체육산업개발주식회사_근무복 주문현황정보 (15168248)\n한국체육산업개발주식회사_정원현황 (15144222)', 'CSV\nCSV\nOpen API'],
    ['타 기관 데이터 2종\n(공동 활용)', '조달청_나라장터쇼핑몰 품목정보 서비스 (15129471)\n한국환경산업기술원_녹색제품 정보조회 서비스 GW (15158379)', 'Open API\nOpen API'],
], [3.6, 11.6, 2.0], size=8.5, bold_first_col=True)

h2('3. 요약')
bullet('**문제**: 근무복은 해마다 사이즈 구성이 바뀌는데(셔츠 105~120 비중 2020년 29% → 2025년 58%) 발주를 담당자의 경험에 의존해 과잉·부족 발주가 생긴다. 이전 3년 사이즈 비율로 최근 3년을 주문하면 셔츠 16%, 점퍼 18%가 어긋난다.')
bullet('**해결**: 근무복 주문 CSV 2종(2017.11~2026.8, 2,867행)을 정제·결합하고, 정원현황으로 인원을 보정해 **품목·사이즈별 발주 수량**을 규칙 기반으로 추천한다(직전 2년 평균, 격년 품목 규칙, 최근 3년 사이즈 비율 3:2:1, 안전재고). 조달청 계약가격으로 금액을, 환경표지 구분으로 녹색구매 비용 차이를 보여주고 **엑셀 발주표**를 내려받게 한다.')
bullet('**특징**: ① 데이터 속 완전 중복 행(주문현황 24.5%, 주문현황정보 21.6%)을 지우지 않고 표시해 \'중복 제거/원자료\' 두 기준을 비교 ② 2022~2025년 **백테스트**로 추천 로직 검증 ③ 적재 방식(CSV↔Open API) 설정 전환으로 망분리 대응 ④ 새 ESG 항목을 모듈로 추가하는 구조.')
bullet('**구성**: ESG 대시보드 · 사이즈 분석 · 발주 추천 · 데이터 관리 · ESG 항목 관리(웹, 로그인). 시연: **https://fitorder.duckdns.org** (user / user1234)')

h2('4. 기대효과')
bullet('**정량(추정)**: 사이즈 미스매치율 16.7% 중 절반을 줄인다고 가정하면 연 **98벌 · 약 442만원 · 탄소 2.17 tCO₂e** 절감(이 기관 근무복 기준, 개선률·단가·탄소계수는 설정값).')
bullet('**업무**: 발주 근거를 사이즈별 수치와 엑셀 발주표로 제시. 주문일의 52%가 하루 10벌 이하의 소량 발주이고 수량의 70%가 4·8월에 몰리므로, 연 2회 정기 발주로 묶도록 제안해 발주 횟수를 줄인다(연평균 28.7일 → 연 2회 제안).')
bullet('**녹색구매·ESG**: 환경표지 제품 여부와 가격 차이(예: 바지 환경표지 제품 +35%)를 구매 결정 때 함께 보여 공공 녹색구매 촉진.')
bullet('**공공데이터 가치**: 개방 데이터의 품질 문제(중복·결측·뜻을 알 수 없는 코드)와 추가 개방 필요 항목을 기관에 환류. 코드는 GitHub에 공개해 다른 공공기관의 물품·ESG 항목으로 확장 가능.')

doc.add_page_break()
HTML.append('<div style="page-break-after:always"></div>')

# ============================================================================================
# 상세 기획서
# ============================================================================================
para('상 세 기 획 서', 18, True, NAVY, WD_ALIGN_PARAGRAPH.CENTER, after=4)

# ---- 1
h1('1. 활용사례 개요')
h2('1-1. 활용사례 명')
para('**핏오더(FitOrder)** — 공공 근무복 주문 데이터 기반 사이즈별 발주 추천 및 ESG 효과 분석 서비스', after=2)

h2('1-2. 활용사례 기능, 특징 등 세부내용')
para('한국체육산업개발(주)가 개방한 근무복 주문 데이터에서 "어떤 품목을, 어떤 사이즈로, 몇 벌 주문해야 하는가"를 계산해 주는 웹 서비스입니다. 담당자는 발주 연도·인원·품목만 고르면 사이즈별 수량과 그 산정 근거를 보고, 엑셀 발주표를 바로 내려받을 수 있습니다.', after=3)
table([
    ['화면(권한)', '주요 기능', '사용 데이터'],
    ['① ESG 대시보드\n(담당자)', '요약 카드 4개(연평균 주문량, 사이즈 미스매치율, 예상 절감액, 탄소 감축량), 연도별 주문량, 완전 중복 행 비중, 월별 집중도, 1인당 지급량', '주문현황·주문현황정보\n정원현황, 조달청'],
    ['② 사이즈 분석\n(담당자)', '품목군별 사이즈 비중을 이전 3년과 최근 3년으로 비교, 이전 비율로는 모자랄·남을 구간 표시, 연도별 사이즈 비중표', '주문현황·주문현황정보'],
    ['③ 발주 추천\n(담당자)', '연도·인원·품목 입력 → 품목별 요약, 사이즈별 수량(안전재고 포함)과 산정 근거 문장, 정기 발주 시기(4·8월), 예상 절감(벌·원·탄소), 녹색구매 비교, 엑셀 발주표, 백테스트', '전체 5종'],
    ['④ 데이터 관리\n(관리자)', 'CSV 업로드·Open API 적재와 이력, 품질 리포트(중복·결측·미상 코드), 단가·시장가·녹색제품 갱신, 사이즈 매핑, 원천 설정(방식·주소·컬럼 매핑)·미리보기', '전체 5종'],
    ['⑤ ESG 항목 관리\n(관리자)', '항목별 사용 여부, 원천별 적재 방식, 망분리 환경용 적재 방식 일괄 전환, 지표 목록·계산 시각', '-'],
], [2.9, 10.6, 3.7], size=8.5, bold_first_col=True)
para('**특징**', 9.5, after=1)
bullet('**설명 가능한 추천**: 머신러닝 대신 담당자가 계산기로 따라 해 볼 수 있는 규칙을 쓰고, 모든 수량에 근거 문장을 붙입니다(예: "직전 2년(2024·2025) 평균 494벌 → 인원 보정 ×0.988 → 안전재고 5%").')
bullet('**데이터를 믿되 숨기지 않음**: 완전 중복 행은 삭제하지 않고 표시만 하며, 모든 화면에서 \'중복 제거/원자료\' 기준을 전환해 비교합니다.')
bullet('**검증된 로직**: 2022~2025년을 한 해씩 모르는 해로 두는 백테스트로 단순 예측(직전 1년, 직전 2년 평균)과 비교하고 결과를 화면에 공개합니다.')
bullet('**운영 친화**: 적재 방식·API 주소·컬럼 이름·안전재고 비율을 코드 수정 없이 설정으로 바꾸고, 외부 API를 못 쓰는 망분리 환경에서는 CSV 업로드로 같은 기능을 씁니다.')

h2('1-3. 활용사례 서비스 확인 방법')
table([
    ['서비스 주소', '**https://fitorder.duckdns.org** (HTTPS, 별도 설치 없이 웹브라우저)'],
    ['시연 계정', '담당자 **user / user1234** (대시보드·사이즈 분석·발주 추천)\n관리자 **admin / admin1234** (위 화면 + 데이터 관리·ESG 항목)'],
    ['확인 순서', '로그인 → ① 대시보드 → ③ 발주 추천("추천 계산", "엑셀 발주표 내려받기") → ② 사이즈 분석 → (관리자) ④ 데이터 관리의 품질 리포트·단가'],
    ['소스 코드', 'https://github.com/kkyeongss/fit-order (서버·화면·배포 스크립트·설명서 공개)'],
], [3.0, 14.2], header=False, size=8.5, bold_first_col=True)
para('※ 시연 서버는 소형 클라우드 서버(메모리 1GB)라 데이터를 올리거나 단가를 바꾼 직후에는 지표를 다시 계산하는 데 수 초~수십 초가 걸릴 수 있습니다. 평소 화면은 0.1~0.5초에 열립니다.', 8.5, False, GRAY, after=2)

# ---- 2
h1('2. 활용사례의 독창성')
h2('2-1. 활용 공공데이터')
table([
    ['데이터(공공데이터포털 번호)', '제공기관 / 형태', '규모·기간', '서비스에서의 역할'],
    ['근무복 주문현황 (15168288)', '한국체육산업개발(주)\nCSV(CP949)', '1,332행\n2018.7~2026.8', '셔츠·재킷·점퍼·패딩조끼의 일자·사이즈(80~125)·수량. **주문량·사이즈 분석의 핵심**'],
    ['근무복 주문현황정보 (15168248)', '한국체육산업개발(주)\nCSV(CP949)', '1,535행\n2017.11~2026.8', '점퍼·바지·조끼의 사용일자·사이즈(10~95)·주문수량. 위 데이터와 점퍼를 품목명으로 결합'],
    ['정원현황 (15144222)', '한국체육산업개발(주)\nOpen API', '기준일자 21개\n2014.1~2026.1', '부서·직군·직급별 정원. 인원 보정과 1인당 지급량의 분모(그해 마지막 기준일자 정원)'],
    ['나라장터쇼핑몰 품목정보 서비스 (15129471)', '조달청\nOpen API(JSON)', '최근 1년 등록\n근무복 424건', '계약가격 → 품목군별 **시장 단가(중앙값)**, 환경표지제품 구분(16건)으로 녹색구매 가격 비교'],
    ['녹색제품 정보조회 서비스 GW (15158379)', '한국환경산업기술원\nOpen API(XML)', '제품명 검색', '환경표지 인증 제품·기업·인증기간 → 발주 추천의 녹색구매 비교(※ 5-3 한계 참고)'],
], [4.2, 3.4, 2.9, 6.7], size=8, bold_first_col=False)
para('**활용 빈도·비중·가공 가능성**: 서비스의 모든 수량 지표(주문량, 사이즈 비율, 미스매치율, 발주 일수, 1인당 지급량, 추천 수량)는 **한국체육산업개발(주) 데이터 3종에서만** 계산하고, 타 기관 2종은 금액·녹색구매 판단을 보조합니다. 개방 데이터는 그대로 쓸 수 없어 다음과 같이 가공했습니다: ① 두 파일의 사이즈 체계(80~125 / 10~95)가 달라 출처를 붙여 구분하고 표준 사이즈 매핑을 입력할 수 있게 함 ② 점퍼 코드 10·11과 두 파일의 점퍼를 하나의 품목으로 통합 ③ 직급별 22개 컬럼으로 된 정원 표를 부서·직군·직급 행으로 변환 ④ 매년 전체 이력이 다시 담겨 나오는 파일을 \'기준연\'으로 관리해 최신 기준연만 집계하고 이전 적재는 이력으로 보관 ⑤ 완전 중복·수량 결측·뜻을 알 수 없는 사이즈 코드에 품질 플래그.', 9, after=3)

h2('2-2. 사례를 활용하게 된 배경 및 활용 우수성')
para('**배경** — 근무복은 연 1,400벌 안팎이 여러 차례 소량으로 주문되는데, 데이터로 보면 사이즈 구성이 해마다 바뀌고(큰 사이즈 증가) 품목별 주문 주기도 다릅니다(재킷은 격년). 개방 데이터에는 이 패턴이 담겨 있지만 담당자가 직접 분석하기는 어렵습니다. 공공데이터 3종을 한 흐름으로 묶으면 "무엇을 얼마나 주문할지"를 근거와 함께 계산할 수 있다고 보았습니다.', 9, after=3)
para('**데이터에서 찾은 사실** (중복 제거 기준, 2020~2025년)', 9.5, after=1)
table([
    ['관찰', '수치', '추천 로직 반영'],
    ['연평균 주문량 / 주문일 수', '1,402벌 / 28.7일, 주문일의 52%가 하루 10벌 이하', '소량 발주를 정기 발주로 묶자는 제안'],
    ['월별 집중', '4월 47% + 8월 23% = 70%', '정기 발주 시기를 4·8월로 제안'],
    ['격년 품목', '재킷 홀수 해 160~190벌, 짝수 해 6~36벌 (패딩조끼도 유사)', '총량에 "같은 홀짝 해의 값" 사용'],
    ['사이즈 변화', '셔츠 105~120 비중 29%(2020) → 58%(2025), 이전 비율 대비 미스매치 셔츠 16.1%·점퍼 17.8%', '최근 3년 비율 3:2:1 가중'],
    ['데이터 품질', '완전 중복 행 327행(24.5%)·331행(21.6%), 2025년 수량의 50%가 중복 표시 행, 수량 결측 41행', '두 기준 병행, 품질 리포트·플래그'],
], [3.4, 8.8, 5.0], size=8, bold_first_col=True)
para('**차별점·창의성**', 9.5, after=1)
bullet('**공개 데이터만으로 동작하는 발주 지원**: 범용 재고·ERP 시스템은 기관이 자체 재고·입출고 데이터를 따로 구축해야 하지만, 핏오더는 이미 개방된 CSV·Open API 형식만으로 곧바로 동작합니다.', 9)
bullet('**효과를 ESG 숫자로 환산**: 사이즈 미스매치를 절감 수량·절감액·탄소 감축량으로 바꿔 보여 "왜 필요한지"를 기관 내부 보고에 바로 쓸 수 있습니다.', 9)
bullet('**데이터 품질을 서비스의 일부로**: 중복·결측·미상 코드를 발견만 하지 않고, 기준 전환·품질 리포트·사이즈 매핑 화면으로 담당자가 직접 다루게 했습니다.', 9)
bullet('**다기관 공공데이터 결합**: 기관 데이터(수량·인원)에 조달청 계약가격과 환경표지 정보를 더해, 수량 예측과 구매 판단(가격·녹색구매)을 한 화면에서 합니다.', 9)

# ---- 3
h1('3. 활용사례의 구체성')
h2('3-1. 처리 흐름')
table([
    ['① 수집', '② 정제', '③ 분석', '④ 추천', '⑤ 제공'],
    ['CSV 업로드\nOpen API 적재\n(같은 파일 중복 적재 방지, 기준연 관리)', '중복 표시, 결측·미상 코드 플래그, 점퍼 통합, 사이즈 매핑, 정원 표 변환', '지표 8개를 두 기준(중복 제거/원자료)으로 미리 계산해 저장', '총량 → 인원 보정 → 사이즈 배분 → 안전재고 → 정기 발주 시기', '웹 화면 5개, 엑셀 발주표, REST API 29개'],
], [3.44, 3.44, 3.44, 3.44, 3.44], size=8)

h2('3-2. 추천 로직 (규칙 기반, 모든 계수는 설정값)')
table([
    ['단계', '규칙', '예: 2027년 셔츠 (정원 1,344명 입력)'],
    ['① 총량', '직전 2년 평균. 재킷·패딩조끼는 격년 주문이라 같은 홀짝 해의 가장 최근 해 값', '2024년 550 + 2025년 437 → 평균 494벌'],
    ['② 인원 보정', '총량 × (입력 인원 ÷ 기준 연도 정원)', '494 × (1,344 ÷ 1,360) = 488벌'],
    ['③ 사이즈 배분', '최근 3년 사이즈 비율을 3:2:1로 가중. 점퍼는 두 출처 비중으로 먼저 나눈 뒤 출처별 비율 적용. 합이 총량과 같게 정수 배분', '100 사이즈 27.6% → 135벌, 105 사이즈 27.0% → 132벌'],
    ['④ 안전재고', '기본 5%, 백테스트 총량 오차 40% 이상인 재킷·바지·패딩조끼는 10%', '+24벌 → **512벌**'],
    ['⑤ 정기 발주', '과거 수량이 몰린 4·8월과 그 비중 제안', '4월 47%, 8월 23%'],
], [2.2, 9.0, 6.0], size=8, bold_first_col=True)
para('2027년 전 품목 추천: **1,369벌**(안전재고 75벌 포함), 예상 금액 6,112만원(셔츠·재킷·바지는 조달청 시장가, 나머지는 추정 단가).', 9, after=3)

h2('3-3. 지표 (적재·단가·사이즈 매핑이 바뀌면 자동 재계산)')
table([
    ['지표', '계산식', '현재 값(중복 제거)'],
    ['사이즈 미스매치율', 'Σ max(0, 최근 3년 비율 − 이전 3년 비율)', '전체 16.7% (셔츠 16.1%, 점퍼 17.8%)'],
    ['절감 수량 / 절감액 / 탄소 감축량', '미스매치율 × 연간 주문량 × 개선률 50% / × 품목별 단가 / × 탄소계수', '연 98벌 / 4,423,336원 / 2.17 tCO₂e'],
    ['연간 발주 일수 · 소량 발주 비율', '두 데이터를 합친 고유 주문일 수 · 하루 10벌 이하 주문일 ÷ 전체', '연평균 28.7일 · 52%'],
    ['1인당 지급량', '연간 주문량 ÷ 그해 마지막 기준일자 정원 (2021년부터)', '2025년 0.97벌'],
], [4.4, 7.6, 5.2], size=8, bold_first_col=True)

h2('3-4. 검증: 백테스트 (2022~2025년, 연도 평균 오차)')
table([
    ['', '직전 1년', '직전 2년 평균', '추천 로직'],
    ['합계 사이즈 오차', '58.4%', '44.4%', '**42.0%** (가장 작음)'],
    ['합계 총량 오차', '17.6%', '21.8%', '20.0%'],
    ['재킷 총량 오차 (격년 품목)', '315%', '154%', '**43%**'],
    ['패딩조끼 총량 오차 (격년 품목)', '360%', '187%', '**99%**'],
    ['셔츠 · 점퍼 총량 오차', '44% · 36%', '28% · 28%', '28% · 28%'],
], [6.4, 3.4, 3.6, 3.8], size=8, bold_first_col=True, align_right_cols=(1, 2, 3))
para('사이즈 오차 = 사이즈별로 남거나 모자란 벌 수의 합 ÷ 실제 총량. 추천은 사이즈 오차가 두 기준선보다 작고 격년 품목 오차를 크게 줄이지만, 총량은 해마다 변동이 커서 한계가 남습니다(안전재고와 정기 발주로 대응).', 8.5, False, GRAY, after=3)

h2('3-5. 화면 (시연 서버에서 캡처)')
picture('dashboard.png', 16.4, '[그림 1] ESG 대시보드 — 요약 카드와 연도별 주문량·중복 행 비중 (완전 중복 행이 반복된 해는 안내 문구로 알림)')
picture('sizes.png', 16.4, '[그림 2] 사이즈 분석 — 이전 3년과 최근 3년 사이즈 비중 비교')
picture('recommend-top.png', 16.4, '[그림 3] 발주 추천 — 입력, 예상 금액·절감·탄소, 품목별 요약(단가 출처 표시)')
picture('recommend-detail.png', 16.4, '[그림 4] 발주 추천 — 품목별 사이즈 수량과 산정 근거, 예상 절감 (엑셀 발주표에도 같은 근거가 들어감)')
picture('admin-prices.png', 15.2, '[그림 5] 데이터 관리 — 단가 출처(입력 > 조달청 시장가 > 추정)와 조달청 시장가 요약')

h2('3-6. 구현 및 품질')
bullet('Java 21 · Spring Boot 4 · PostgreSQL 18 · React 19 · TypeScript · Chart.js. 항목·적재 방식·품질 점검·지표·외부 데이터를 인터페이스로 나눠, 새 ESG 항목은 클래스를 추가하는 것만으로 화면과 API에 나타납니다.', 9)
bullet('실제 공공데이터로 수치(중복 327·331행, 연평균 1,402벌, 미스매치율, 백테스트 오차)를 재현하는 자동 테스트 81개(서버 74, 화면 7). 계산 결과는 데이터가 바뀌면 즉시 새로 계산해 낡은 값이 남지 않음을 테스트로 확인했습니다.', 9)

# ---- 4
h1('4. 사례의 활용 적절성 (기대되는 사회적 가치)')
bullet('**공공 예산 절감과 자원 절약**: 사이즈가 맞지 않아 남거나 모자란 근무복을 줄여 연 98벌·약 442만원(추정)을 아끼고, 같은 방식으로 다른 공공기관의 단체 지급 물품에도 적용할 수 있습니다.', 9)
bullet('**탄소 감축·녹색구매 촉진**: 절감 수량에 품목별 탄소계수를 곱해 연 2.17 tCO₂e를 계산하고, 환경표지 제품이 있는지와 가격 차이(조달청 쇼핑몰 바지 기준 환경표지 제품 중앙값 54,500원 vs 전체 40,500원)를 구매 시점에 보여 줘 녹색구매를 현실적으로 검토하게 합니다.', 9)
bullet('**공공데이터 품질 환류**: 중복 행 비중(2025년 50%), 수량 결측, 뜻을 알 수 없는 사이즈 코드(91·95)를 품질 리포트로 정리해 기관의 개방 데이터 품질 개선에 활용할 수 있습니다.', 9)
bullet('**투명한 의사결정**: 추천 수량마다 근거 문장과 엑셀 근거 열을 남겨, 담당자 교체와 무관하게 같은 기준으로 발주하고 감사·ESG 보고에 설명할 수 있습니다.', 9)
bullet('**재사용과 확산**: 소스를 공개하고 항목을 모듈로 추가하는 구조라, 사회공헌·입출차 등 이 기관의 다른 ESG 데이터나 다른 기관으로 넓힐 수 있습니다.', 9)

# ---- 5
h1('5. 실현가능성 및 기타')
h2('5-1. 추진 일정 및 개발·운영 계획')
table([
    ['단계', '기간', '내용', '상태'],
    ['1. 핵심 기능 구현', '2026.11.27~11.28', '적재·정제, 지표 8개, 발주 추천·백테스트, 엑셀, 외부 데이터 2종 연동, 웹 화면 5개, 로그인', '완료'],
    ['2. 시연 서버 공개', '2026.11.28', 'Oracle Cloud 서버에 배포, HTTPS 도메인 연결, 실제 데이터 적재', '완료'],
    ['3. 제출', '2026.12.1', '기획서·화면 캡처·소스 공개', '진행'],
    ['4. 수상 시 고도화', '~2027.3', '기관 실제 단가와 구매요청 양식 반영, 부서별 분석(정원 연계), 기관 내부망 이관(Docker 이미지·CSV 업로드 방식)', '계획'],
    ['5. 운영·확장', '2027~2028', '연 1회 데이터 갱신 대응, 사회공헌·입출차 등 ESG 모듈 추가, 운영 협조', '계획'],
], [3.4, 3.0, 9.4, 1.4], size=8, bold_first_col=True)

h2('5-2. 서비스 유지 계획 (2028년까지 운영 가능 여부)')
bullet('**운영 가능**: 소형 클라우드 서버(메모리 1GB)와 무료 도메인(DuckDNS)으로 현재 운영 중이라 운영비가 거의 들지 않으며, 데이터 갱신은 연 1회 CSV를 올리는 몇 분짜리 작업입니다. 소스·배포 스크립트·설명서가 GitHub에 있어 담당자가 바뀌어도 재현할 수 있습니다.', 9)
bullet('**이관 대비**: 서버를 옮겨야 하면 포함된 Docker 이미지와 서버 설정 스크립트로 기관 서버에 올릴 수 있고, 외부 API를 쓸 수 없는 내부망에서는 데이터 관리 화면에서 적재 방식을 파일 업로드로 일괄 전환합니다(조달청·녹색제품 데이터도 CSV 업로드 지원).', 9)
bullet('**위험과 대응**: 서버가 작아(메모리 1GB) 재시작 때 일시적으로 느려질 수 있어 계산 결과를 메모리에 보관하고 시작 직후 미리 계산하도록 했으며, 필요하면 기관 서버로 이관합니다. 컬럼명이 바뀌면 코드 수정 없이 설정 화면에서 매핑을 바꿉니다.', 9)

h2('5-3. 한계와 보완 계획')
bullet('절감 효과(개선률 50%), 점퍼·조끼·패딩조끼 단가(조달청 표본 5건 미만이라 추정 53,000원), 탄소계수(Carbonfact 중앙값)는 **가정값**입니다. 설정·화면에서 바꿀 수 있고, 기관의 실제 단가를 입력하면 절감액이 즉시 다시 계산됩니다.', 9)
bullet('완전 중복 행이 실제 재주문인지 입력 중복인지는 데이터만으로 알 수 없어 두 기준을 함께 제공합니다(기관 확인 필요).', 9)
bullet('한국환경산업기술원 녹색제품 정보조회 서비스는 연동 코드를 완성했으나, 이 기획서를 작성한 2026.11.28 기준으로 기관 서버가 오류(HTTP_ERROR)를 반환해 인증 제품 수·기업 표시는 응답이 정상화되는 대로 적용됩니다. 조달청 데이터의 환경표지 구분으로 한 녹색구매 가격 비교는 현재 동작합니다.', 9)

h2('5-4. 추가 개방이 필요한 공공데이터에 대한 의견')
bullet('**사이즈 코드표**: 두 파일의 사이즈 체계(10~95, 80~125) 대응표와 뜻을 알 수 없는 코드(91, 95)의 설명', 9)
bullet('**주문 식별자와 부서**: 주문번호(또는 주문건 식별자)와 주문 부서·직군 컬럼 — 완전 중복 행의 원인 확인과 부서별 지급량 분석(정원현황과 연결)', 9)
bullet('**지급 기준과 재고·폐기**: 직군·직급별 지급 기준과 주기, 입고·재고·폐기 수량(현재는 \'주문\'만 있음), 주문 취소·반품', 9)
bullet('**계약 단가와 성별 구분**: 품목별 계약 단가, 성별코드가 한 값(10)뿐이라 성별 분석 불가 → 코드 보완', 9)

doc.save(OUT)
print('저장:', OUT)
if PREVIEW:
    css = f'''body{{font-family:'{FONT}','Apple SD Gothic Neo',sans-serif;margin:0}}
@page{{size:A4;margin:1.7cm 1.9cm 1.6cm 1.9cm}}
.p{{line-height:{LINE};word-break:keep-all}} .h1{{border-bottom:0.75pt solid #1F3A5F;padding-bottom:1pt}}
table.t{{border-collapse:collapse;table-layout:fixed;width:{CONTENT_W}cm;margin:0 auto}}
table.t td{{border:0.5pt solid #000;padding:2pt 3.5pt;line-height:{LINE};vertical-align:top;word-break:keep-all}}'''
    PREVIEW.write_text('<!doctype html><meta charset="utf-8"><style>' + css + '</style>' + ''.join(HTML), encoding='utf-8')
    print('미리보기:', PREVIEW)
