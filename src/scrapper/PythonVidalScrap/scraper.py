#!/usr/bin/env python3
"""
Vidal.ru Scraper v5.2
- Полный перечень лекарственных форм Минздрава РФ
- SQLAlchemy ORM
- Параллельная запись в PostgreSQL + CSV
- Healthcheck и отказоустойчивость
- Настраиваемые пути данных
"""

import argparse
import csv
import json
import logging
import os
import random
import re
import signal
import sys
import time
import threading
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from typing import Optional, Dict, Any
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from sqlalchemy import create_engine, Column, Integer, String, Boolean, Text, DateTime, func, text
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.exc import OperationalError

from form_types import FORM_TYPES

# === LOGGING ===
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)],
    force=True
)
logger = logging.getLogger(__name__)

# === CONFIG ===
BASE_URL = "https://www.vidal.ru"
PRODUCTS_URL = f"{BASE_URL}/drugs/products"

MIN_DELAY = float(os.getenv('MIN_DELAY', '3.0'))
MAX_DELAY = float(os.getenv('MAX_DELAY', '7.0'))
MAX_RETRIES = int(os.getenv('MAX_RETRIES', '5'))
INITIAL_BACKOFF = 30
MAX_BACKOFF = 600
HTTP_TIMEOUT = int(os.getenv('HTTP_TIMEOUT', '30'))
CONNECT_TIMEOUT = int(os.getenv('CONNECT_TIMEOUT', '10'))

# PostgreSQL - fallback to SQLite if not set or connection fails
DB_URL = os.getenv('DATABASE_URL', '')
FALLBACK_DB_URL = os.getenv('FALLBACK_DB_URL', 'sqlite:///vidal_drugs.db')

# Paths - configurable via environment or command line
DEFAULT_DATA_DIR = os.getenv('DATA_DIR', './data')

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/121.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/121.0.0.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/121.0.0.0",
]

# Global health status
health_status: Dict[str, Any] = {
    "status": "starting",
    "db_connected": False,
    "last_scrape": None,
    "scraped_count": 0,
    "error_count": 0,
}


# === HEALTHCHECK HTTP SERVER ===
class HealthHandler(BaseHTTPRequestHandler):
    """HTTP handler for healthcheck endpoint."""
    
    def log_message(self, format, *args):
        pass  # Suppress HTTP logs
    
    def do_GET(self):
        global health_status
        if self.path == '/health' or self.path == '/':
            status_code = 200 if health_status.get("status") == "running" else 503
            self.send_response(status_code)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(health_status, default=str).encode())
        else:
            self.send_response(404)
            self.end_headers()


def start_health_server(port: int = 8080):
    """Start healthcheck HTTP server in background thread."""
    try:
        server = HTTPServer(('0.0.0.0', port), HealthHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        logger.info(f"Healthcheck server started on port {port}")
    except Exception as e:
        logger.warning(f"Could not start healthcheck server: {e}")


def extract_form_type(form_text: str) -> str:
    """Извлекает тип формы по перечню Минздрава"""
    if not form_text:
        return ""
    
    form_lower = form_text.lower()
    
    for pattern, form_type in FORM_TYPES:
        if re.search(pattern, form_lower):
            return form_type
    
    # Fallback: первые 3 слова (очищенные)
    words = form_text.split()[:3]
    result = ' '.join(words).lower()
    result = re.sub(r'[\d.,:%]+.*$', '', result).strip()
    return result[:60] if result else ""


# === ORM ===
Base = declarative_base()


class Drug(Base):
    __tablename__ = 'drugs'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    drug_id = Column(String(30), unique=True, nullable=False, index=True)
    
    name = Column(String(300), nullable=False, index=True)
    name_lat = Column(String(300))
    form = Column(String(500))
    form_type = Column(String(100), index=True)
    quantity = Column(Integer, default=0)
    quantity_unit = Column(String(30))  # Unit type: шт, мл, мг, г, доз, ампул, флаконов, etc.
    dosage = Column(String(100))
    
    active_substance = Column(String(300), index=True)
    category = Column(String(300))
    description = Column(Text)
    manufacturer = Column(String(300), index=True)
    country = Column(String(100))
    otc = Column(Boolean, default=None)
    
    url = Column(String(300))
    created_at = Column(DateTime, default=datetime.utcnow)
    
    def to_dict(self):
        return {
            'drug_id': self.drug_id,
            'name': self.name,
            'name_lat': self.name_lat or '',
            'form': self.form or '',
            'form_type': self.form_type or '',
            'quantity': self.quantity or 0,
            'quantity_unit': self.quantity_unit or '',
            'dosage': self.dosage or '',
            'active_substance': self.active_substance or '',
            'category': self.category or '',
            'description': self.description or '',
            'manufacturer': self.manufacturer or '',
            'country': self.country or '',
            'otc': self.otc,
            'url': self.url or '',
        }


# === HELPERS ===
def truncate(text: str, max_len: int) -> str:
    if not text:
        return ""
    text = re.sub(r'\s+', ' ', text.strip())
    return text[:max_len-3] + "..." if len(text) > max_len else text


def extract_drug_names(drug_title: str) -> tuple:
    """Extract Russian and Latin names from drug title.
    
    Approach based on medicine_scrapper's extract_drug_names function.
    Returns: (ru_title, en_title, full_title)
    """
    if not drug_title:
        return None, None, None
    
    # Remove trademark symbol and normalize brackets
    drug_title = drug_title.replace('®', '').replace('[', '(').replace(']', ')')
    drug_title = re.sub(r'\s*(ОПИСАНИЕ|описание|инструкция по применению).*$', '', drug_title)
    drug_title = re.sub(r'\s+', ' ', drug_title).strip()
    
    # Pattern to find English/Latin text in parentheses at the end
    # Allow starting with letter or number (for cases like 5-NOK)
    # Allow trailing numbers after the closing paren (like medicine_scrapper test cases)
    pattern_en = re.compile(r'\(([a-zA-Z0-9][a-zA-Z0-9\s\-\+\.\/]+)\)\s*(?:\d+)?$', re.IGNORECASE)
    
    match = pattern_en.search(drug_title)
    if match:
        en_title = match.group(1).strip()
        ru_title = drug_title[:match.start()].strip()
        # Only accept as English name if it contains at least one Latin letter
        if ru_title and re.search(r'[a-zA-Z]', en_title):
            return ru_title, en_title, f"{ru_title} ({en_title})"
    
    # No English name found - return original title
    return drug_title, None, drug_title


def extract_quantity(form_text: str) -> tuple:
    """Extract quantity and unit from form text. Returns (quantity, unit).
    Handles various unit types:
    - шт (pieces/tablets)
    - № (number indicator)
    - мл (milliliters for syrups, solutions)
    - мг (milligrams for powders)
    - г (grams)
    - саше, пакетиков (sachets)
    - ампул (ampoules)
    - флаконов, флак (vials)
    - доз (doses)
    - капсул (capsules count)
    - таблеток (tablets count)
    """
    if not form_text:
        return 0, ''
    
    # Priority patterns - more specific first, with unit names
    patterns = [
        # N шт., : N шт
        (r':\s*(\d+)\s*шт', 'шт'),
        (r'(\d+)\s*шт(?:\.|$|\s|,)', 'шт'),
        # № N (number indicator - usually means pieces)
        (r'№\s*(\d+)', 'шт'),
        # N ампул/флаконов (ampoules/vials count)
        (r'(\d+)\s*ампул', 'ампул'),
        (r'(\d+)\s*(?:флакон|фл\.)', 'флаконов'),
        # N доз (doses)
        (r'(\d+)\s*доз', 'доз'),
        # N саше/пакетик (sachets)
        (r'(\d+)\s*саше', 'саше'),
        (r'(\d+)\s*пакетик', 'пакетиков'),
        # N капс./табл. (capsules/tablets explicit count)
        (r'(\d+)\s*капс\.?\s*(?:$|[,;])', 'капсул'),
        (r'(\d+)\s*табл\.?\s*(?:$|[,;])', 'таблеток'),
        # Generic: N мл for volumes (syrups, solutions)
        (r'(?:^|[\s,;:])(\d+)\s*мл(?:\s|$|[,;])', 'мл'),
        # N г (grams for powders)
        (r'(?:^|[\s,;:])(\d+)\s*г(?:\s|$|[,;])', 'г'),
    ]
    
    for pattern, unit in patterns:
        match = re.search(pattern, form_text, re.I)
        if match:
            qty = int(match.group(1))
            # Reasonable quantity range for pharmaceutical products
            if 1 <= qty <= 10000:
                return qty, unit
    return 0, ''


def extract_dosage(form_text: str) -> str:
    if not form_text:
        return ""
    match = re.search(r'(\d+(?:[.,]\d+)?\s*(?:мг|г|мкг|МЕ|ЕД|%|мл)(?:/(?:мл|г|доз[ау]?|сут|ч))?)', form_text, re.I)
    return match.group(1).strip() if match else ""


def make_description(soup, page_text: str) -> str:
    parts = []
    
    # Pharmacotherapeutic group (from medicine_scrapper: div#phthgroup)
    phthgroup = soup.find(id='phthgroup')
    if phthgroup:
        text = phthgroup.get_text(strip=True)
        text = re.sub(r'^Фармакотерапевтическая\s+группа\s*', '', text, flags=re.I)
        text = truncate(text, 300)
        if text:
            parts.append(f"Группа: {text}")
    
    influence = soup.find(id='influence')
    if influence:
        text = influence.get_text(strip=True)
        text = re.sub(r'^Фармакологическое действие\s*', '', text)
        sentences = re.split(r'(?<=[.!?])\s+', text)[:2]
        if sentences:
            parts.append(' '.join(sentences))
    
    indication = soup.find(id='indication')
    if indication:
        text = indication.get_text(strip=True)
        text = re.sub(r'^Показания[^А-Яа-я]*препарата\s+\w+\s*', '', text, flags=re.I)
        text = re.sub(r'\s*[A-Z]\d{2}[.\s].*$', '', text)
        text = truncate(text, 500)
        if text:
            parts.append(f"Показания: {text}")
    
    contra = soup.find(id='contra')
    if contra:
        text = contra.get_text(strip=True)
        text = re.sub(r'^Противопоказания[^А-Яа-я]*', '', text, flags=re.I)
        text = truncate(text, 300)
        if text:
            parts.append(f"Противопоказания: {text}")
    
    side = soup.find(id='side_effects')
    if side:
        text = side.get_text(strip=True)
        text = re.sub(r'^Побочное действие\s*', '', text, flags=re.I)
        text = truncate(text, 300)
        if text:
            parts.append(f"Побочные эффекты: {text}")
    
    dosage_sec = soup.find(id='dosage')
    if dosage_sec:
        text = dosage_sec.get_text(strip=True)
        text = re.sub(r'^Режим дозирования\s*', '', text, flags=re.I)
        text = truncate(text, 300)
        if text:
            parts.append(f"Применение: {text}")
    
    storage = re.search(r'(?:Температура хранения:?\s*)?(от\s*\d+\s*до\s*\d+\s*°[CС])', page_text)
    if storage:
        parts.append(f"Хранение: {storage.group(1)}")
    
    if re.search(r'ЖНВЛП|Жизненно необходим', page_text):
        parts.append("Входит в список ЖНВЛП")
    
    return '\n\n'.join(parts)


class CSVWriter:
    def __init__(self, filepath: Path):
        self.filepath = filepath
        self.fieldnames = [
            'drug_id', 'name', 'name_lat', 'form', 'form_type', 'quantity', 'quantity_unit',
            'dosage', 'active_substance', 'category', 'description',
            'manufacturer', 'country', 'otc', 'url'
        ]
        self._init_file()
    
    def _init_file(self):
        if not self.filepath.exists():
            self.filepath.parent.mkdir(parents=True, exist_ok=True)
            with open(self.filepath, 'w', newline='', encoding='utf-8') as f:
                writer = csv.DictWriter(f, fieldnames=self.fieldnames)
                writer.writeheader()
            logger.info(f"Created CSV: {self.filepath}")
    
    def write(self, drug: Drug):
        with open(self.filepath, 'a', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=self.fieldnames)
            writer.writerow(drug.to_dict())


class VidalScraper:
    def __init__(self, db_url: str = None, data_dir: Path = None):
        global health_status
        
        self.data_dir = data_dir or Path(DEFAULT_DATA_DIR)
        self.csv_file = self.data_dir / 'drugs.csv'
        self.progress_file = self.data_dir / 'progress.json'
        
        # Try to create data directory
        try:
            self.data_dir.mkdir(parents=True, exist_ok=True)
            logger.info(f"Data directory: {self.data_dir}")
        except PermissionError as e:
            logger.error(f"Cannot create data directory {self.data_dir}: {e}")
            logger.info("Use --data-dir to specify a writable directory")
            raise
        
        # Database connection with fallback
        self.db_url = self._init_database(db_url)
        
        self.csv_writer = CSVWriter(self.csv_file)
        self.http = requests.Session()
        self.backoff = INITIAL_BACKOFF
        self.progress = self._load_progress()
        self.stats = {"scraped": 0, "errors": 0, "requests": 0}
        
        health_status["status"] = "running"
        health_status["db_connected"] = True
        
        logger.info(f"Database: {self.db_url}")
        logger.info(f"CSV: {self.csv_file}")
    
    def _init_database(self, db_url: str = None) -> str:
        """Initialize database with fallback to SQLite."""
        global health_status
        
        url = db_url or DB_URL or FALLBACK_DB_URL
        
        # If PostgreSQL URL is provided, try to connect
        if 'postgresql' in url:
            try:
                logger.info(f"Connecting to PostgreSQL: {url.split('@')[-1] if '@' in url else url}")
                self.engine = create_engine(url, echo=False, connect_args={'connect_timeout': CONNECT_TIMEOUT})
                # Test connection
                with self.engine.connect() as conn:
                    conn.execute(text("SELECT 1"))
                Base.metadata.create_all(self.engine)
                Session = sessionmaker(bind=self.engine)
                self.session = Session()
                logger.info("PostgreSQL connection successful")
                return url
            except OperationalError as e:
                logger.warning(f"PostgreSQL connection failed: {e}")
                logger.info(f"Falling back to SQLite: {FALLBACK_DB_URL}")
                url = FALLBACK_DB_URL
            except Exception as e:
                logger.warning(f"Database connection error: {e}")
                url = FALLBACK_DB_URL
        
        # SQLite fallback
        if 'sqlite' in url:
            # Ensure SQLite file is in data directory
            if url == FALLBACK_DB_URL and not url.startswith('sqlite:///'):
                url = f"sqlite:///{self.data_dir / 'vidal_drugs.db'}"
            elif url == 'sqlite:///vidal_drugs.db':
                url = f"sqlite:///{self.data_dir / 'vidal_drugs.db'}"
        
        self.engine = create_engine(url, echo=False)
        Base.metadata.create_all(self.engine)
        Session = sessionmaker(bind=self.engine)
        self.session = Session()
        health_status["db_connected"] = True
        return url
    
    def _load_progress(self) -> dict:
        if self.progress_file.exists():
            try:
                with open(self.progress_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError, OSError) as e:
                logger.warning(f"Failed to load progress from {self.progress_file}: {e}")
        return {"completed": [], "syllables_done": []}
    
    def _save_progress(self):
        try:
            self.progress_file.parent.mkdir(parents=True, exist_ok=True)
            with open(self.progress_file, 'w', encoding='utf-8') as f:
                json.dump(self.progress, f, ensure_ascii=False)
        except (IOError, OSError) as e:
            logger.warning(f"Failed to save progress: {e}")
    
    def _get_headers(self):
        return {
            "User-Agent": random.choice(USER_AGENTS),
            "Accept-Language": "ru-RU,ru;q=0.9",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        }
    
    def _fetch(self, url: str, retry: int = 0) -> Optional[BeautifulSoup]:
        """Fetch URL with retries and proper timeout handling."""
        if retry >= MAX_RETRIES:
            logger.warning(f"Max retries ({MAX_RETRIES}) exceeded for {url}")
            return None
        
        try:
            time.sleep(random.uniform(MIN_DELAY, MAX_DELAY))
            resp = self.http.get(
                url,
                headers=self._get_headers(),
                timeout=(CONNECT_TIMEOUT, HTTP_TIMEOUT)  # (connect, read) timeouts
            )
            self.stats["requests"] += 1
            
            if resp.status_code in (429, 503):
                wait = min(self.backoff * (2 ** retry), MAX_BACKOFF)
                logger.warning(f"Rate limit ({resp.status_code}), waiting {wait}s before retry {retry + 1}...")
                time.sleep(wait)
                return self._fetch(url, retry + 1)
            
            if resp.status_code == 404:
                logger.debug(f"Page not found: {url}")
                return None
            
            resp.raise_for_status()
            self.backoff = INITIAL_BACKOFF
            return BeautifulSoup(resp.text, 'lxml')
            
        except requests.exceptions.Timeout as e:
            logger.warning(f"Timeout fetching {url}: {e} (retry {retry + 1}/{MAX_RETRIES})")
            time.sleep(min(self.backoff, 60))
            return self._fetch(url, retry + 1)
        except requests.exceptions.ConnectionError as e:
            logger.warning(f"Connection error for {url}: {e} (retry {retry + 1}/{MAX_RETRIES})")
            time.sleep(min(self.backoff * (retry + 1), MAX_BACKOFF))
            return self._fetch(url, retry + 1)
        except Exception as e:
            logger.warning(f"Error fetching {url}: {type(e).__name__}: {e}")
            time.sleep(self.backoff)
            return self._fetch(url, retry + 1)
    
    def get_syllables(self) -> list:
        """Get list of syllable pages to scrape from /drugs/products/o.
        
        Uses the approach from medicine_scrapper - only get main letters (А, Б, В...).
        Each main letter page already includes ALL products starting with that letter,
        including sub-combinations (Аа, Аб, Ав, etc.) via pagination.
        
        This is the most complete source containing:
        - All drugs (лекарства)
        - БАДы (dietary supplements)  
        - Medical devices (медицинские изделия)
        - Discontinued drugs (с недействующими рег. уд.)
        """
        syllables = set()
        
        # Fetch the main products page /drugs/products/o
        main_page = self._fetch(f"{PRODUCTS_URL}/o")
        if main_page:
            # Get only MAIN letters (first level) - not sub-combinations
            # Main letters are direct children of div.letters-list (not in div.letters-sub)
            for letters_list in main_page.select('div.syllables div.letters-list'):
                for link in letters_list.find_all('a', class_='letter', recursive=False):
                    href = link.get('href', '')
                    # Convert absolute URL to relative path
                    if href.startswith(BASE_URL):
                        href = href[len(BASE_URL):]
                    if href.startswith('/drugs/products/o/') and href != '/drugs/products/o/':
                        syllables.add(href)
        
        if not syllables:
            logger.error("Could not fetch syllable pages from /drugs/products/o")
            return []
        
        logger.info(f"Found {len(syllables)} syllable pages")
        return sorted(syllables)
    
    def get_product_urls(self, syllable: str) -> list:
        """Get all product URLs from a syllable page, including pagination.
        
        Collects ALL drugs including:
        - Active drugs (current registration)
        - Discontinued drugs (с недействующими рег. уд. / not-valid-status)
        - БАДы (dietary supplements)
        - Medical devices
        """
        urls = []
        current_page_url = urljoin(BASE_URL, syllable)
        
        # Exclude category/index pages that are not actual drugs
        exclude = ['/drugs/products', '/drugs/firm/', '/drugs/molecule/', '/drugs/atc',
                   '/drugs/nosology', '/drugs/clinic', '/drugs/pharm', '/analog',
                   '/drugs/molecules', '/drugs/companies', '/drugs/disease',
                   '/drugs/interaction', '/drugs/encyclopedia']
        
        while current_page_url:
            soup = self._fetch(current_page_url)
            if not soup:
                break
            
            # Collect ALL drug links from products table
            # This includes both active and discontinued drugs (tr.not-valid-status)
            for product_cell in soup.select('table.products-table td.products-table-name a.no-underline'):
                href = product_cell.get('href', '')
                if href and href.startswith('/drugs/') and href not in urls:
                    if not any(e in href for e in exclude):
                        urls.append(href)
            
            # Also check for any drug links we might have missed
            for product_link in soup.select('table.products-table a[href^="/drugs/"]'):
                href = product_link.get('href', '')
                if href and href not in urls:
                    if not any(e in href for e in exclude):
                        # Only add actual drug pages (not category pages)
                        parts = href.split('/')
                        if len(parts) >= 3 and parts[2] and not href.endswith('/products'):
                            urls.append(href)
            
            # Handle pagination - look for "next" page link
            next_link = soup.select_one('span.next > a')
            if next_link and next_link.get('href'):
                current_page_url = urljoin(BASE_URL, next_link.get('href'))
            else:
                break
        
        return urls
    
    def parse_product(self, url: str) -> Optional[Drug]:
        full_url = urljoin(BASE_URL, url)
        soup = self._fetch(full_url)
        if not soup:
            return None
        
        drug = Drug()
        drug.url = full_url
        
        # Extract drug_id from URL - try multiple patterns (similar to medicine_scrapper)
        # Pattern 1: __XXXXX at end of URL
        # Pattern 2: _XXXXX at end of URL
        # Pattern 3: extract from page title (XXXXX)
        id_match = re.search(r'_+(\d+)$', url)
        if id_match:
            drug.drug_id = id_match.group(1)
        else:
            # Try to extract from page title as fallback
            title_tag = soup.find('title')
            if title_tag:
                title_id_match = re.search(r'\((\d+)\)\s*[-–]?\s*справочник', title_tag.get_text(), re.I)
                if title_id_match:
                    drug.drug_id = title_id_match.group(1)
            if not drug.drug_id:
                drug.drug_id = url.split('/')[-1][:30]
        
        # Extract drug name - improved extraction using extract_drug_names (like medicine_scrapper)
        # Try to get from h1 with class "relative" first
        h1 = soup.select_one('div.relative h1')
        if not h1:
            h1 = soup.find('h1')
        
        if h1:
            title = h1.get_text(strip=True)
            # Use our improved extract_drug_names function
            ru_name, en_name, full_title = extract_drug_names(title)
            if ru_name:
                drug.name = truncate(ru_name, 300)
                if en_name:
                    drug.name_lat = truncate(en_name, 300)
            else:
                # Fallback to simple extraction
                title = re.sub(r'\s*(ОПИСАНИЕ|описание).*$', '', title)
                match = re.match(r'^([^(]+)\s*\(([^)]+)\)', title)
                if match:
                    drug.name = truncate(match.group(1).strip(), 300)
                    drug.name_lat = truncate(match.group(2).strip(), 300)
                else:
                    drug.name = truncate(title, 300)
        
        if not drug.name:
            return None
        
        firm = soup.find('a', href=re.compile(r'/drugs/firm/\d+'))
        if firm:
            drug.manufacturer = truncate(firm.get_text(strip=True), 300)
            parent = firm.find_parent()
            if parent:
                m = re.search(r'\(([А-Яа-яЁё\s,]+)\)', parent.get_text())
                if m:
                    drug.country = truncate(m.group(1).strip(), 100)
        
        mol = soup.find('a', href=re.compile(r'/drugs/molecule/\d+'))
        if mol:
            drug.active_substance = truncate(mol.get_text(strip=True), 300)
        
        cfg = soup.find('a', href=re.compile(r'/drugs/clinic-group/\d+'))
        if cfg:
            drug.category = truncate(cfg.get_text(strip=True), 300)
        
        # Форма выпуска - improved extraction
        form_full = ""
        
        # Method 1: Extract from title tag (most reliable)
        title_tag = soup.find('title')
        if title_tag:
            title_text = title_tag.get_text()
            # Pattern: "... описание DrugName FORM_HERE (ID)..."
            # Capture Russian form description after drug name
            patterns = [
                # After "описание DrugName " capture Russian text until ( or end
                r'описание\s+\w+\s+([а-яёА-ЯЁ][а-яёА-ЯЁ\s]+(?:для\s+[а-яё\s]+)?(?:введения|применения|внутрь)?\s*\d*[^(]*)',
                # After dash + drug name, capture form  
                r'–\s+(?:описание\s+)?\w+\s+([а-яёА-ЯЁ][а-яё\s]+(?:для\s+[а-яё\s]+)?)',
            ]
            for pattern in patterns:
                m = re.search(pattern, title_text, re.I)
                if m:
                    form_full = m.group(1).strip()
                    form_full = re.sub(r'\s*\(\d+\).*$', '', form_full)
                    form_full = re.sub(r'\s+', ' ', form_full).strip()
                    break
        
        # Method 2: Look for form in "forms" section
        if not form_full:
            forms_section = soup.find(id='forms')
            if forms_section:
                # Find first row with form information
                for row in forms_section.find_all('tr'):
                    cells = row.find_all(['td', 'th'])
                    for cell in cells:
                        text = cell.get_text(strip=True)
                        # Look for typical form patterns
                        if re.search(r'(таблетк|капсул|раствор|порошок|мазь|крем|гель|капли|суспензи|сироп|спрей|аэрозоль)', text, re.I):
                            form_full = text
                            break
                    if form_full:
                        break
        
        # Method 3: Look in tables for form column (fallback)
        if not form_full:
            for table in soup.find_all('table'):
                for row in table.find_all('tr'):
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 3:
                        text = cells[2].get_text(strip=True)
                        if re.search(r'(таблетк|капсул|раствор|мг|мл)', text, re.I):
                            form_full = re.sub(r'\s*рег\..*$', '', text, flags=re.I)
                            break
                if form_full:
                    break
        
        if form_full:
            form_full = re.sub(r'\s+', ' ', form_full).strip()
            drug.form = truncate(form_full, 500)
            drug.form_type = extract_form_type(form_full)
            drug.quantity, drug.quantity_unit = extract_quantity(form_full)
            drug.dosage = extract_dosage(form_full)
        
        page_text = soup.get_text()
        if re.search(r'Без рецепта|безрецептурн', page_text, re.I):
            drug.otc = True
        elif re.search(r'По рецепту|рецептурн', page_text, re.I):
            drug.otc = False
        
        drug.description = make_description(soup, page_text)
        
        return drug
    
    def save_drug(self, drug: Drug):
        try:
            existing = self.session.query(Drug).filter_by(drug_id=drug.drug_id).first()
            if existing:
                for key, value in drug.to_dict().items():
                    if key != 'drug_id':
                        setattr(existing, key, value)
            else:
                self.session.add(drug)
            self.session.commit()
            self.csv_writer.write(drug)
        except Exception as e:
            logger.error(f"Save error: {e}")
            self.session.rollback()
            raise
    
    def run(self, limit: int = None):
        global health_status
        
        logger.info("=" * 60)
        logger.info("Vidal.ru Scraper v5.2")
        logger.info("=" * 60)
        
        syllables = self.get_syllables()
        if not syllables:
            logger.error("No syllables found! Check network connection to vidal.ru")
            health_status["status"] = "error"
            return
        
        completed = set(self.progress.get("completed", []))
        syllables_done = set(self.progress.get("syllables_done", []))
        total = 0
        
        for syl_idx, syllable in enumerate(syllables):
            if syllable in syllables_done:
                continue
            
            logger.info(f"Processing syllable [{syl_idx+1}/{len(syllables)}]: {syllable}")
            urls = self.get_product_urls(syllable)
            logger.info(f"  Found {len(urls)} products on this page")
            
            for url in urls:
                if limit and total >= limit:
                    logger.info(f"Reached limit of {limit} drugs")
                    self._save_progress()
                    return
                
                if url in completed:
                    continue
                
                try:
                    drug = self.parse_product(url)
                    if drug:
                        self.save_drug(drug)
                        self.stats["scraped"] += 1
                        total += 1
                        health_status["scraped_count"] = self.stats["scraped"]
                        health_status["last_scrape"] = datetime.utcnow().isoformat()
                        qty_display = f"{drug.quantity} {drug.quantity_unit}" if drug.quantity and drug.quantity_unit else (str(drug.quantity) if drug.quantity else 'N/A')
                        logger.info(f"  ✓ [{total}] {drug.name} | Form: {drug.form_type or 'N/A'} | Qty: {qty_display}")
                    else:
                        self.stats["errors"] += 1
                        health_status["error_count"] = self.stats["errors"]
                        logger.debug(f"  ✗ Could not parse {url}")
                    
                    completed.add(url)
                    self.progress["completed"] = list(completed)
                    
                    if total % 10 == 0:
                        self._save_progress()
                
                except KeyboardInterrupt:
                    logger.info("Interrupted by user, saving progress...")
                    self._save_progress()
                    raise
                except Exception as e:
                    logger.error(f"  Error processing {url}: {type(e).__name__}: {e}")
                    self.stats["errors"] += 1
                    health_status["error_count"] = self.stats["errors"]
            
            syllables_done.add(syllable)
            self.progress["syllables_done"] = list(syllables_done)
            self._save_progress()
            logger.info(f"  Completed syllable {syllable}, total scraped: {total}")
        
        count = self.session.query(Drug).count()
        logger.info("=" * 60)
        logger.info(f"Scraping complete! Total drugs in database: {count}")
        logger.info(f"Stats: scraped={self.stats['scraped']}, errors={self.stats['errors']}, requests={self.stats['requests']}")
        health_status["status"] = "completed"
    
    def get_stats(self):
        try:
            total = self.session.query(Drug).count()
            by_form = dict(self.session.query(Drug.form_type, func.count()).group_by(Drug.form_type).all())
            return {
                "total": total,
                "by_form_type": by_form,
                "csv_file": str(self.csv_file),
                "db_url": self.db_url.split('@')[-1] if '@' in self.db_url else self.db_url,
                "health": health_status
            }
        except Exception as e:
            return {"error": str(e), "health": health_status}


def test_form_extraction():
    """Тест извлечения форм по перечню Минздрава"""
    test_cases = [
        ("Таблетки 200 мг: 10 шт.", "таблетки"),
        ("Таблетки, покрытые пленочной оболочкой 500 мг", "таблетки покрытые пленочной оболочкой"),
        ("Таблетки, покрытые оболочкой 100 мг", "таблетки покрытые оболочкой"),
        ("Таблетки с пролонгированным высвобождением 75 мг", "таблетки с пролонгированным высвобождением"),
        ("Таблетки кишечнорастворимые 25 мг", "таблетки кишечнорастворимые"),
        ("Таблетки жевательные 100 мг", "таблетки жевательные"),
        ("Таблетки для рассасывания 3 мг", "таблетки для рассасывания"),
        ("Таблетки шипучие 1000 мг", "таблетки шипучие"),
        ("Таблетки подъязычные 40 мг", "таблетки подъязычные"),
        ("Капсулы 500 мг", "капсулы"),
        ("Капсулы с пролонгированным высвобождением 150 мг", "капсулы с пролонгированным высвобождением"),
        ("Капсулы кишечнорастворимые 20 мг", "капсулы кишечнорастворимые"),
        ("Раствор для внутривенного введения 10 мг/мл", "раствор для внутривенного введения"),
        ("Раствор для внутримышечного введения 25 мг/мл", "раствор для внутримышечного введения"),
        ("Раствор для в/в и в/м введения 50 мг/мл", "раствор для внутривенного и внутримышечного введения"),
        ("Раствор для инфузий 500 мл", "раствор для инфузий"),
        ("Раствор для инъекций 100 мг/мл", "раствор для инъекций"),
        ("Раствор для приема внутрь 10 мг/мл", "раствор для приема внутрь"),
        ("Раствор для ингаляций 0.5 мг/мл", "раствор для ингаляций"),
        ("Раствор для наружного применения 1%", "раствор для наружного применения"),
        ("Концентрат для приготовления раствора для инфузий 100 мг/16.7 мл", "концентрат для раствора для инфузий"),
        ("Лиофилизат для приготовления раствора для внутривенного введения 250 МЕ", "лиофилизат для раствора для внутривенного введения"),
        ("Лиофилизат для приготовления раствора для инфузий 500 мг", "лиофилизат для раствора для инфузий"),
        ("Порошок для приготовления раствора для приема внутрь 3 г", "порошок для раствора для приема внутрь"),
        ("Порошок для приготовления суспензии для приема внутрь 125 мг/5мл", "порошок для суспензии для приема внутрь"),
        ("Капли глазные 0.5%", "капли глазные"),
        ("Капли глазные пролонгированного действия 0.25%", "капли глазные пролонгированные"),
        ("Капли назальные 0.1%", "капли назальные"),
        ("Капли ушные 1%", "капли ушные"),
        ("Капли для приема внутрь 100 мг/мл", "капли для приема внутрь"),
        ("Суппозитории ректальные 100 мг", "суппозитории ректальные"),
        ("Суппозитории вагинальные 200 мг", "суппозитории вагинальные"),
        ("Спрей назальный дозированный 50 мкг/доза", "спрей назальный дозированный"),
        ("Спрей назальный 0.05%", "спрей назальный"),
        ("Аэрозоль для ингаляций дозированный 100 мкг/доза", "аэрозоль для ингаляций дозированный"),
        ("Мазь для наружного применения 2%", "мазь для наружного применения"),
        ("Мазь глазная 3%", "мазь глазная"),
        ("Крем для наружного применения 1%", "крем для наружного применения"),
        ("Гель для наружного применения 1%", "гель для наружного применения"),
        ("Гель глазной 0.2%", "гель глазной"),
        ("Суспензия для приема внутрь 100 мг/5 мл", "суспензия для приема внутрь"),
        ("Сироп 15 мг/5 мл", "сироп"),
        ("Пластырь трансдермальный 4.6 мг/24 ч", "пластырь трансдермальный"),
        ("Гранулы для приготовления суспензии", "гранулы для суспензии"),
        ("Драже 50 мг", "драже"),
        ("Пастилки 8.75 мг", "пастилки лекарственные"),
        ("Леденцы 1.2 мг", "леденцы лекарственные"),
        ("Настойка 100 мл", "настойка"),
        ("Эликсир 100 мл", "эликсир"),
        ("Бальзам для наружного применения", "бальзам"),
    ]
    
    print("=" * 80)
    print("Тест извлечения лекарственных форм по перечню Минздрава РФ")
    print("=" * 80)
    
    passed = 0
    failed = []
    for form_text, expected in test_cases:
        result = extract_form_type(form_text)
        if result == expected:
            passed += 1
            print(f"✓ '{form_text[:50]}' → '{result}'")
        else:
            failed.append((form_text, expected, result))
            print(f"✗ '{form_text[:50]}' → '{result}' (ожидалось '{expected}')")
    
    print(f"\nПрошло: {passed}/{len(test_cases)}")
    if failed:
        print(f"\nНе прошли ({len(failed)}):")
        for form_text, expected, result in failed:
            print(f"  '{form_text}' → '{result}' (ожидалось '{expected}')")
    
    return len(failed) == 0


def test_live_scraping(timeout: int = 90, data_dir: Path = None):
    """Тест парсинга на реальных данных с таймаутом"""
    import tempfile
    
    print("\n" + "=" * 80)
    print(f"Тест на реальных данных vidal.ru (таймаут {timeout}с)")
    print("=" * 80)
    
    # Use temp directory for tests
    test_dir = data_dir or Path(tempfile.mkdtemp(prefix='vidal_test_'))
    
    # Таймаут
    def timeout_handler(signum, frame):
        raise TimeoutError("Тест превысил таймаут!")
    
    signal.signal(signal.SIGALRM, timeout_handler)
    signal.alarm(timeout)
    
    try:
        test_db = f"sqlite:///{test_dir / 'test_vidal.db'}"
        
        scraper = VidalScraper(db_url=test_db, data_dir=test_dir)
        
        # Тестовые препараты с разными формами - using validated URLs from actual scraping
        test_urls = [
            "/drugs/5-nok__5470",              # Таблетки покрытые оболочкой
            "/drugs/5-nok__18909",             # Таблетки покрытые оболочкой (другая форма)
            "/drugs/l-thyroxin__23557",        # Таблетки
            "/drugs/vabysmo",                  # Раствор для внутриглазного введения
            "/drugs/5-hydroxytryptophan-5-htp-100-mg",  # Капсулы
        ]
        
        results = []
        for url in test_urls:
            logger.info(f"Парсинг: {url}")
            try:
                drug = scraper.parse_product(url)
                if drug:
                    scraper.save_drug(drug)
                    results.append(drug)
                    logger.info(f"  ✓ {drug.name} | {drug.form_type or 'N/A'}")
                else:
                    logger.warning(f"  ✗ Не удалось спарсить {url} (parse_product вернул None)")
            except Exception as e:
                logger.error(f"  ✗ Ошибка парсинга {url}: {type(e).__name__}: {e}")
        
        signal.alarm(0)
        
        print("\n" + "=" * 80)
        print("Результаты парсинга:")
        print("=" * 80)
        for drug in results:
            print(f"\n{drug.name} ({drug.name_lat or '-'}):")
            print(f"  Форма полностью: {drug.form or '-'}")
            print(f"  Тип формы: {drug.form_type or '-'}")
            print(f"  Дозировка: {drug.dosage or '-'}")
            print(f"  Количество: {drug.quantity or '-'}")
            print(f"  Производитель: {drug.manufacturer or '-'}")
            print(f"  Страна: {drug.country or '-'}")
            print(f"  Категория: {drug.category or '-'}")
            print(f"  Рецепт: {'Без рецепта' if drug.otc else 'По рецепту' if drug.otc is False else '-'}")
        
        # CSV
        if scraper.csv_file.exists():
            with open(scraper.csv_file, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            print(f"\nCSV: {len(lines)-1} записей")
        
        # Статистика типов форм
        print("\nТипы форм:")
        for drug in results:
            if drug.form_type:
                print(f"  - {drug.form_type}")
        
        # Show summary
        print(f"\nУспешно спарсено: {len(results)}/{len(test_urls)} препаратов")
        
        # Return True if at least 2 drugs were parsed (some URLs may change)
        return len(results) >= 2
        
    except TimeoutError as e:
        print(f"ОШИБКА: {e}")
        return False
    except Exception as e:
        print(f"ОШИБКА: {type(e).__name__}: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        signal.alarm(0)
        # Cleanup temp files
        try:
            import shutil
            if 'test_dir' in dir() and test_dir and test_dir.exists():
                shutil.rmtree(test_dir, ignore_errors=True)
        except:
            pass


def main():
    parser = argparse.ArgumentParser(
        description='Vidal.ru Pharmaceutical Database Scraper v5.2',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python scraper.py                       # Start scraping with default settings
  python scraper.py --data-dir ./mydata   # Use custom data directory
  python scraper.py --limit 100           # Scrape only 100 drugs
  python scraper.py --stats               # Show database statistics
  python scraper.py --test                # Run form extraction tests
  python scraper.py --test-live           # Test live scraping
  python scraper.py --healthcheck         # Start with healthcheck server
        """
    )
    parser.add_argument('--limit', type=int, help='Limit number of drugs to scrape')
    parser.add_argument('--data-dir', type=str, default=DEFAULT_DATA_DIR,
                        help=f'Directory for data files (default: {DEFAULT_DATA_DIR})')
    parser.add_argument('--db-url', type=str, help='Database URL (default: from DATABASE_URL env or SQLite)')
    parser.add_argument('--stats', action='store_true', help='Show database statistics')
    parser.add_argument('--reset', action='store_true', help='Reset scraping progress')
    parser.add_argument('--test', action='store_true', help='Run form extraction tests')
    parser.add_argument('--test-live', action='store_true', help='Test on live data from vidal.ru')
    parser.add_argument('--healthcheck', action='store_true', help='Start healthcheck HTTP server')
    parser.add_argument('--healthcheck-port', type=int, default=8080, help='Healthcheck server port (default: 8080)')
    args = parser.parse_args()
    
    # Form extraction test (no data dir needed)
    if args.test:
        success = test_form_extraction()
        sys.exit(0 if success else 1)
    
    # Live scraping test
    if args.test_live:
        data_dir = Path(args.data_dir)
        success = test_live_scraping(data_dir=data_dir)
        sys.exit(0 if success else 1)
    
    # Setup data directory
    data_dir = Path(args.data_dir)
    try:
        data_dir.mkdir(parents=True, exist_ok=True)
    except PermissionError as e:
        logger.error(f"Cannot create data directory '{data_dir}': {e}")
        logger.error("Use --data-dir to specify a writable directory")
        sys.exit(1)
    
    # Start healthcheck server if requested
    if args.healthcheck:
        start_health_server(args.healthcheck_port)
    
    # Handle reset
    progress_file = data_dir / 'progress.json'
    if args.reset:
        if progress_file.exists():
            progress_file.unlink()
        logger.info("Progress reset")
    
    # Initialize scraper
    try:
        scraper = VidalScraper(db_url=args.db_url, data_dir=data_dir)
    except Exception as e:
        logger.error(f"Failed to initialize scraper: {e}")
        sys.exit(1)
    
    # Show stats only
    if args.stats:
        stats = scraper.get_stats()
        print(json.dumps(stats, indent=2, ensure_ascii=False, default=str))
        return
    
    # Run scraper
    try:
        scraper.run(limit=args.limit)
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
    finally:
        scraper.session.close()


if __name__ == "__main__":
    main()
