import sqlite3
from datetime import datetime, timedelta
from dataclasses import dataclass
from typing import Optional

DB_NAME = 'equipment.db'

# Database initialization
conn = sqlite3.connect(DB_NAME)

conn.execute('''CREATE TABLE IF NOT EXISTS equipment (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    manufacturer TEXT,
    purchase_date TEXT,
    install_date TEXT,
    maintenance_expiration TEXT,
    lifespan_years INTEGER
)''')

conn.execute('''CREATE TABLE IF NOT EXISTS license (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    equipment_id INTEGER,
    license_type TEXT,
    issue_date TEXT,
    expiration_date TEXT,
    key TEXT,
    FOREIGN KEY(equipment_id) REFERENCES equipment(id)
)''')

conn.execute('''CREATE TABLE IF NOT EXISTS contract (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor TEXT,
    contact TEXT,
    start_date TEXT,
    end_date TEXT,
    sla TEXT
)''')

conn.execute('''CREATE TABLE IF NOT EXISTS maintenance_request (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    equipment_id INTEGER,
    request_date TEXT,
    resolved_date TEXT,
    description TEXT,
    result TEXT,
    FOREIGN KEY(equipment_id) REFERENCES equipment(id)
)''')

conn.commit()


def add_equipment(name: str, manufacturer: str, purchase_date: str, install_date: str,
                  maintenance_expiration: str, lifespan_years: int):
    conn.execute('''INSERT INTO equipment
                    (name, manufacturer, purchase_date, install_date, maintenance_expiration, lifespan_years)
                    VALUES (?, ?, ?, ?, ?, ?)''',
                 (name, manufacturer, purchase_date, install_date, maintenance_expiration, lifespan_years))
    conn.commit()


def add_license(equipment_id: int, license_type: str, issue_date: str, expiration_date: str, key: str):
    conn.execute('''INSERT INTO license
                    (equipment_id, license_type, issue_date, expiration_date, key)
                    VALUES (?, ?, ?, ?, ?)''',
                 (equipment_id, license_type, issue_date, expiration_date, key))
    conn.commit()


def add_contract(vendor: str, contact: str, start_date: str, end_date: str, sla: str):
    conn.execute('''INSERT INTO contract
                    (vendor, contact, start_date, end_date, sla)
                    VALUES (?, ?, ?, ?, ?)''',
                 (vendor, contact, start_date, end_date, sla))
    conn.commit()


def add_maintenance_request(equipment_id: int, request_date: str, description: str, result: str,
                             resolved_date: Optional[str] = None):
    conn.execute('''INSERT INTO maintenance_request
                    (equipment_id, request_date, resolved_date, description, result)
                    VALUES (?, ?, ?, ?, ?)''',
                 (equipment_id, request_date, resolved_date, description, result))
    conn.commit()


def upcoming_replacements():
    today = datetime.today().date()
    cursor = conn.execute('''SELECT id, name, purchase_date, lifespan_years FROM equipment''')
    for row in cursor:
        purchase_date = datetime.strptime(row[2], '%Y-%m-%d').date()
        lifespan = row[3]
        replace_date = purchase_date + timedelta(days=lifespan*365)
        if (replace_date - today).days <= 90:  # 3 months ahead
            print(f"Equipment {row[1]} (id {row[0]}) should be replaced by {replace_date}")


def upcoming_license_expirations():
    today = datetime.today().date()
    cursor = conn.execute('''SELECT id, equipment_id, license_type, expiration_date FROM license''')
    for row in cursor:
        exp_date = datetime.strptime(row[3], '%Y-%m-%d').date()
        if (exp_date - today).days <= 30:
            print(f"License {row[2]} (id {row[0]}) for equipment {row[1]} expires on {exp_date}")


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Equipment and License Management')
    subparsers = parser.add_subparsers(dest='command')

    # Equipment
    parser_add_e = subparsers.add_parser('add_equipment')
    parser_add_e.add_argument('--name', required=True)
    parser_add_e.add_argument('--manufacturer')
    parser_add_e.add_argument('--purchase_date', required=True)
    parser_add_e.add_argument('--install_date', required=True)
    parser_add_e.add_argument('--maintenance_expiration')
    parser_add_e.add_argument('--lifespan', type=int, default=5)

    # License
    parser_add_l = subparsers.add_parser('add_license')
    parser_add_l.add_argument('--equipment_id', type=int, required=True)
    parser_add_l.add_argument('--type', required=True)
    parser_add_l.add_argument('--issue_date', required=True)
    parser_add_l.add_argument('--expiration_date', required=True)
    parser_add_l.add_argument('--key', required=True)

    # Contract
    parser_add_c = subparsers.add_parser('add_contract')
    parser_add_c.add_argument('--vendor', required=True)
    parser_add_c.add_argument('--contact', required=True)
    parser_add_c.add_argument('--start_date', required=True)
    parser_add_c.add_argument('--end_date', required=True)
    parser_add_c.add_argument('--sla', default='')

    # Maintenance request
    parser_add_m = subparsers.add_parser('add_request')
    parser_add_m.add_argument('--equipment_id', type=int, required=True)
    parser_add_m.add_argument('--date', required=True)
    parser_add_m.add_argument('--desc', required=True)
    parser_add_m.add_argument('--result', default='')
    parser_add_m.add_argument('--resolved_date')

    # Alerts
    subparsers.add_parser('check_replacements')
    subparsers.add_parser('check_licenses')

    args = parser.parse_args()

    if args.command == 'add_equipment':
        add_equipment(args.name, args.manufacturer, args.purchase_date, args.install_date,
                      args.maintenance_expiration, args.lifespan)
    elif args.command == 'add_license':
        add_license(args.equipment_id, args.type, args.issue_date, args.expiration_date, args.key)
    elif args.command == 'add_contract':
        add_contract(args.vendor, args.contact, args.start_date, args.end_date, args.sla)
    elif args.command == 'add_request':
        add_maintenance_request(args.equipment_id, args.date, args.desc, args.result, args.resolved_date)
    elif args.command == 'check_replacements':
        upcoming_replacements()
    elif args.command == 'check_licenses':
        upcoming_license_expirations()
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
