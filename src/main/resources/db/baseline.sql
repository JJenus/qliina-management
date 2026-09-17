--
-- PostgreSQL database dump
--

\restrict 5EgqTbEymbrfRg1hcho2M6xMgXroM1Cqa1qvfUPjfkTlFf9xzR89md4YRX6KMmD

-- Dumped from database version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: attendance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    approved_at timestamp(6) without time zone,
    approved_by uuid,
    check_in_time timestamp(6) without time zone,
    check_out_time timestamp(6) without time zone,
    date date NOT NULL,
    employee_id uuid NOT NULL,
    notes character varying(255),
    overtime_hours double precision,
    status character varying(255) NOT NULL,
    total_hours double precision,
    CONSTRAINT attendance_status_check CHECK (((status)::text = ANY (ARRAY[('PRESENT'::character varying)::text, ('ABSENT'::character varying)::text, ('LATE'::character varying)::text, ('HALF_DAY'::character varying)::text, ('HOLIDAY'::character varying)::text, ('LEAVE'::character varying)::text, ('SICK'::character varying)::text])))
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    action character varying(255) NOT NULL,
    business_id uuid,
    category character varying(255),
    changes jsonb,
    details text,
    device_id character varying(255),
    entity_display character varying(255),
    entity_id uuid,
    entity_type character varying(255) NOT NULL,
    execution_time_ms bigint,
    ip_address character varying(255),
    new_value jsonb,
    old_value jsonb,
    request_id character varying(255),
    request_method character varying(255),
    request_path character varying(255),
    response_status integer,
    retention_until timestamp(6) without time zone,
    session_id character varying(255),
    severity character varying(255),
    shop_id uuid,
    "timestamp" timestamp(6) without time zone NOT NULL,
    user_agent character varying(255),
    user_email character varying(255),
    user_id uuid,
    user_name character varying(255),
    CONSTRAINT audit_logs_severity_check CHECK (((severity)::text = ANY (ARRAY[('INFO'::character varying)::text, ('WARNING'::character varying)::text, ('ERROR'::character varying)::text, ('CRITICAL'::character varying)::text])))
);


--
-- Name: auth_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_credentials (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    failed_attempts integer,
    last_login timestamp(6) without time zone,
    locked_until timestamp(6) without time zone,
    password_hash character varying(255) NOT NULL,
    password_last_changed timestamp(6) without time zone,
    totp_enabled boolean,
    totp_secret character varying(255),
    user_id uuid NOT NULL
);


--
-- Name: billing_coupon_redemptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_coupon_redemptions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    coupon_id uuid NOT NULL,
    redeemed_at timestamp(6) without time zone NOT NULL,
    subscription_id uuid
);


--
-- Name: billing_coupons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_coupons (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    code character varying(40) NOT NULL,
    discount_type character varying(20) NOT NULL,
    discount_value numeric(12,2) NOT NULL,
    expires_at timestamp(6) without time zone,
    max_redemptions integer,
    max_redemptions_per_business integer,
    redemptions_count integer NOT NULL,
    starts_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    CONSTRAINT billing_coupons_discount_type_check CHECK (((discount_type)::text = ANY (ARRAY[('PERCENT'::character varying)::text, ('FIXED_AMOUNT'::character varying)::text]))),
    CONSTRAINT billing_coupons_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('DISABLED'::character varying)::text, ('EXPIRED'::character varying)::text])))
);


--
-- Name: billing_invoice_counters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_invoice_counters (
    counter_year integer NOT NULL,
    next_value bigint NOT NULL
);


--
-- Name: billing_invoice_line_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_invoice_line_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(12,2) NOT NULL,
    description text,
    period_end timestamp(6) without time zone,
    period_start timestamp(6) without time zone,
    type character varying(30) NOT NULL,
    invoice_id uuid NOT NULL,
    CONSTRAINT billing_invoice_line_items_type_check CHECK (((type)::text = ANY (ARRAY[('SUBSCRIPTION'::character varying)::text, ('PRORATION_CREDIT'::character varying)::text, ('PRORATION_CHARGE'::character varying)::text, ('ONE_OFF'::character varying)::text, ('TAX'::character varying)::text])))
);


--
-- Name: billing_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_invoices (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(12,2) NOT NULL,
    due_at timestamp(6) without time zone,
    idempotency_key character varying(255),
    invoice_number character varying(40) NOT NULL,
    issued_at timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    subscription_id uuid NOT NULL,
    CONSTRAINT billing_invoices_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('OPEN'::character varying)::text, ('PAID'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: billing_payment_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_payment_methods (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    gateway_customer_id character varying(128),
    gateway_method_id character varying(128),
    is_default boolean NOT NULL,
    label character varying(120),
    type character varying(20) NOT NULL,
    CONSTRAINT billing_payment_methods_type_check CHECK (((type)::text = ANY (ARRAY[('CARD'::character varying)::text, ('BANK'::character varying)::text])))
);


--
-- Name: billing_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_payments (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(12,2) NOT NULL,
    fee_amount numeric(12,2) NOT NULL,
    gateway character varying(30) NOT NULL,
    gateway_response text,
    gateway_transaction_id character varying(128) NOT NULL,
    idempotency_key character varying(255),
    invoice_id uuid NOT NULL,
    net_amount numeric(12,2) NOT NULL,
    paid_at timestamp(6) without time zone,
    payment_method_id uuid,
    status character varying(20) NOT NULL,
    CONSTRAINT billing_payments_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('FAILED'::character varying)::text, ('REFUNDED'::character varying)::text, ('DISPUTED'::character varying)::text])))
);


--
-- Name: billing_plan_features; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_plan_features (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    feature_key character varying(64) NOT NULL,
    is_hard_limit boolean NOT NULL,
    feature_value character varying(255) NOT NULL,
    plan_id uuid NOT NULL
);


--
-- Name: billing_plan_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_plan_versions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    currency character varying(3) NOT NULL,
    effective_from timestamp(6) without time zone NOT NULL,
    price numeric(12,2) NOT NULL,
    plan_id uuid NOT NULL
);


--
-- Name: billing_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_plans (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    archived_at timestamp(6) without time zone,
    description text,
    name character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT billing_plans_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('DEPRECATED'::character varying)::text, ('ARCHIVED'::character varying)::text])))
);


--
-- Name: billing_subscription_coupons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_subscription_coupons (
    created_at timestamp(6) without time zone NOT NULL,
    coupon_id uuid NOT NULL,
    subscription_id uuid NOT NULL
);


--
-- Name: billing_subscription_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_subscription_events (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    from_status character varying(20),
    occurred_at timestamp(6) without time zone NOT NULL,
    reason text,
    subscription_id uuid NOT NULL,
    to_status character varying(20) NOT NULL,
    CONSTRAINT billing_subscription_events_from_status_check CHECK (((from_status)::text = ANY (ARRAY[('TRIALING'::character varying)::text, ('ACTIVE'::character varying)::text, ('PAST_DUE'::character varying)::text, ('CANCELED'::character varying)::text]))),
    CONSTRAINT billing_subscription_events_to_status_check CHECK (((to_status)::text = ANY (ARRAY[('TRIALING'::character varying)::text, ('ACTIVE'::character varying)::text, ('PAST_DUE'::character varying)::text, ('CANCELED'::character varying)::text])))
);


--
-- Name: billing_subscription_features; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_subscription_features (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    feature_key character varying(64) NOT NULL,
    overridden_at timestamp(6) without time zone,
    feature_value character varying(255) NOT NULL,
    subscription_id uuid NOT NULL
);


--
-- Name: billing_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_subscriptions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    cancel_at_period_end boolean NOT NULL,
    current_period_end timestamp(6) without time zone,
    current_period_start timestamp(6) without time zone,
    next_retry_at timestamp(6) without time zone,
    pending_change_at timestamp(6) without time zone,
    retry_count integer NOT NULL,
    status character varying(20) NOT NULL,
    trial_ends_at timestamp(6) without time zone,
    pending_plan_id uuid,
    plan_id uuid NOT NULL,
    plan_version_id uuid NOT NULL,
    CONSTRAINT billing_subscriptions_status_check CHECK (((status)::text = ANY (ARRAY[('TRIALING'::character varying)::text, ('ACTIVE'::character varying)::text, ('PAST_DUE'::character varying)::text, ('CANCELED'::character varying)::text])))
);


--
-- Name: billing_usage_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.billing_usage_records (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    feature_key character varying(64) NOT NULL,
    quantity numeric(12,2) NOT NULL,
    recorded_at timestamp(6) without time zone NOT NULL,
    subscription_id uuid NOT NULL
);


--
-- Name: business_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_config (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    allow_negative_inventory boolean,
    auto_archive_days integer,
    business_id uuid NOT NULL,
    currency character varying(10),
    currency_locale character varying(20),
    currency_symbol character varying(10),
    date_format character varying(255),
    day_cutoff_time character varying(255),
    default_shift_hours integer,
    hourly_rate numeric(8,2),
    idle_timeout_minutes integer,
    invoice_prefix character varying(255),
    loyalty_points_per_dollar integer,
    min_redeemable_points integer,
    email_notifications boolean,
    order_confirmation boolean,
    order_ready boolean,
    payment_receipt boolean,
    reminder_before_due integer,
    sms_notifications boolean,
    whatsapp_notifications boolean,
    order_prefix character varying(255),
    receipt_prefix character varying(255),
    require_clock_in_roles character varying(500),
    require_quality_check boolean,
    tax_rate numeric(5,2),
    timezone character varying(255)
);


--
-- Name: businesses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.businesses (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    address_line1 character varying(255),
    address_line2 character varying(255),
    city character varying(255),
    country character varying(255),
    latitude double precision,
    longitude double precision,
    postal_code character varying(255),
    state character varying(255),
    email character varying(120),
    logo_url character varying(500),
    name character varying(120) NOT NULL,
    phone character varying(20),
    onboarding_completed_at timestamp(6) without time zone,
    plan character varying(20) NOT NULL,
    slug character varying(80) NOT NULL,
    status character varying(20) NOT NULL,
    trial_ends_at timestamp(6) without time zone,
    CONSTRAINT businesses_plan_check CHECK (((plan)::text = ANY (ARRAY[('FREE'::character varying)::text, ('STARTER'::character varying)::text, ('PRO'::character varying)::text, ('ENTERPRISE'::character varying)::text]))),
    CONSTRAINT businesses_status_check CHECK (((status)::text = ANY (ARRAY[('TRIAL'::character varying)::text, ('ACTIVE'::character varying)::text, ('SUSPENDED'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


--
-- Name: cash_drawer_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_drawer_sessions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    actual_closing_cash numeric(10,2),
    closed_at timestamp(6) without time zone,
    closed_by uuid,
    difference numeric(10,2),
    expected_closing_cash numeric(10,2),
    notes character varying(255),
    opened_at timestamp(6) without time zone NOT NULL,
    opened_by uuid NOT NULL,
    starting_cash numeric(10,2) NOT NULL,
    status character varying(255)
);


--
-- Name: check_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.check_results (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    checklist_item_id uuid,
    notes character varying(255),
    passed boolean,
    quality_check_id uuid NOT NULL
);


--
-- Name: checklist_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    description character varying(255) NOT NULL,
    failure_severity character varying(255),
    item_order integer,
    required boolean,
    checklist_id uuid NOT NULL
);


--
-- Name: compliance_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compliance_reports (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    file_size bigint,
    file_url character varying(255),
    generated_at timestamp(6) without time zone NOT NULL,
    generated_by uuid,
    notes character varying(255),
    period_end timestamp(6) without time zone,
    period_start timestamp(6) without time zone,
    report_data jsonb,
    report_number character varying(255) NOT NULL,
    report_type character varying(255) NOT NULL,
    status character varying(255)
);


--
-- Name: consent_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consent_records (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    consent_type character varying(255) NOT NULL,
    consent_version character varying(255),
    customer_id uuid NOT NULL,
    document_url character varying(255),
    expires_at timestamp(6) without time zone,
    granted boolean NOT NULL,
    granted_at timestamp(6) without time zone,
    ip_address character varying(255),
    notes character varying(255),
    revoked_at timestamp(6) without time zone,
    revoked_by uuid,
    revoked_ip character varying(255),
    status character varying(255),
    user_agent character varying(255),
    CONSTRAINT consent_records_consent_type_check CHECK (((consent_type)::text = ANY (ARRAY[('MARKETING'::character varying)::text, ('SMS'::character varying)::text, ('EMAIL'::character varying)::text, ('DATA_PROCESSING'::character varying)::text, ('TERMS_AND_CONDITIONS'::character varying)::text, ('PRIVACY_POLICY'::character varying)::text, ('COOKIES'::character varying)::text]))),
    CONSTRAINT consent_records_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('EXPIRED'::character varying)::text, ('REVOKED'::character varying)::text, ('SUPERSEDED'::character varying)::text])))
);


--
-- Name: corporate_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.corporate_accounts (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    address_line1 character varying(255),
    address_line2 character varying(255),
    city character varying(255),
    country character varying(255),
    postal_code character varying(255),
    state character varying(255),
    billing_cycle character varying(255),
    company_name character varying(255) NOT NULL,
    credit_limit numeric(10,2) NOT NULL,
    current_balance numeric(10,2),
    customer_id uuid NOT NULL,
    last_invoice_date timestamp(6) without time zone,
    last_payment_date timestamp(6) without time zone,
    payment_terms character varying(255),
    status character varying(255),
    tax_id character varying(255)
);


--
-- Name: customer_addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_addresses (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    address_line1 character varying(255) NOT NULL,
    address_line2 character varying(255),
    city character varying(255) NOT NULL,
    country character varying(255),
    instructions character varying(255),
    is_default boolean,
    postal_code character varying(255),
    state character varying(255),
    type character varying(255),
    customer_id uuid NOT NULL
);


--
-- Name: customer_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_notes (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    content character varying(2000) NOT NULL,
    type character varying(255),
    customer_id uuid NOT NULL
);


--
-- Name: customer_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_preferences (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    delivery_instructions character varying(255),
    fabric_care text[],
    notify_via_email boolean,
    notify_via_sms boolean,
    preferred_payment_method character varying(255),
    preferred_shop_id uuid,
    customer_id uuid NOT NULL
);


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    average_order_value numeric(10,2),
    email character varying(255),
    enabled boolean,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    last_order_date timestamp(6) without time zone,
    loyalty_points integer,
    loyalty_tier character varying(255),
    phone character varying(255) NOT NULL,
    rfm_segment character varying(255),
    tags text[],
    total_orders integer,
    total_spent numeric(10,2)
);


--
-- Name: data_retention_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_retention_policies (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    archive_enabled boolean,
    archive_location character varying(255),
    delete_enabled boolean,
    entity_type character varying(255) NOT NULL,
    is_active boolean,
    last_run timestamp(6) without time zone,
    next_run timestamp(6) without time zone,
    notification_days_before integer,
    retention_days integer NOT NULL
);


--
-- Name: data_subject_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_subject_requests (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    assigned_to uuid,
    completed_at timestamp(6) without time zone,
    completed_by uuid,
    customer_email character varying(255),
    customer_id uuid,
    customer_name character varying(255),
    data_export_url character varying(255),
    due_date timestamp(6) without time zone,
    notes character varying(255),
    request_details text,
    request_number character varying(255) NOT NULL,
    request_type character varying(255) NOT NULL,
    response_details text,
    status character varying(255) NOT NULL,
    submitted_at timestamp(6) without time zone NOT NULL,
    verification_method character varying(255),
    verification_status boolean,
    CONSTRAINT data_subject_requests_request_type_check CHECK (((request_type)::text = ANY (ARRAY[('ACCESS'::character varying)::text, ('RECTIFICATION'::character varying)::text, ('ERASURE'::character varying)::text, ('RESTRICTION'::character varying)::text, ('PORTABILITY'::character varying)::text, ('OBJECTION'::character varying)::text]))),
    CONSTRAINT data_subject_requests_status_check CHECK (((status)::text = ANY (ARRAY[('SUBMITTED'::character varying)::text, ('VERIFYING'::character varying)::text, ('IN_PROGRESS'::character varying)::text, ('COMPLETED'::character varying)::text, ('REJECTED'::character varying)::text, ('EXPIRED'::character varying)::text])))
);


--
-- Name: defect_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_images (
    defect_id uuid NOT NULL,
    image_url character varying(255)
);


--
-- Name: defects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defects (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    assigned_to uuid,
    compensation numeric(10,2),
    compensation_type character varying(255),
    description character varying(255),
    location character varying(255),
    reported_at timestamp(6) without time zone,
    reported_by uuid,
    resolution character varying(255),
    severity character varying(255) NOT NULL,
    status character varying(255),
    type character varying(255) NOT NULL,
    quality_check_id uuid NOT NULL
);


--
-- Name: email_configurations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_configurations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    from_address character varying(255),
    from_name character varying(255),
    host character varying(255),
    is_configured boolean,
    password_encrypted character varying(255),
    port integer,
    use_ssl boolean,
    use_tls boolean,
    username character varying(255)
);


--
-- Name: employee_performance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_performance (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    attendance_rate double precision,
    customer_satisfaction double precision,
    employee_id uuid NOT NULL,
    items_processed integer,
    notes character varying(255),
    ontime_rate double precision,
    orders_processed integer,
    period_end date NOT NULL,
    period_start date NOT NULL,
    quality_score double precision,
    revenue_handled numeric(10,2),
    rework_rate double precision,
    target_achievement double precision
);


--
-- Name: employee_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_schedules (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    date date,
    day_of_week character varying(255),
    employee_id uuid NOT NULL,
    end_time time(0) without time zone NOT NULL,
    is_active boolean,
    is_recurring boolean,
    notes character varying(255),
    recurring_pattern character varying(255),
    role character varying(255),
    start_time time(0) without time zone NOT NULL,
    CONSTRAINT employee_schedules_day_of_week_check CHECK (((day_of_week)::text = ANY (ARRAY[('MONDAY'::character varying)::text, ('TUESDAY'::character varying)::text, ('WEDNESDAY'::character varying)::text, ('THURSDAY'::character varying)::text, ('FRIDAY'::character varying)::text, ('SATURDAY'::character varying)::text, ('SUNDAY'::character varying)::text])))
);


--
-- Name: employee_shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_shifts (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    actual_end timestamp(6) without time zone,
    actual_start timestamp(6) without time zone,
    approved_at timestamp(6) without time zone,
    approved_by uuid,
    auto_closed boolean,
    break_end timestamp(6) without time zone,
    break_start timestamp(6) without time zone,
    date date NOT NULL,
    employee_id uuid NOT NULL,
    last_activity_at timestamp(6) without time zone,
    notes character varying(255),
    overtime_minutes integer,
    scheduled_end timestamp(6) without time zone NOT NULL,
    scheduled_start timestamp(6) without time zone NOT NULL,
    status character varying(255),
    suspended_at timestamp(6) without time zone,
    total_break_minutes integer,
    total_suspend_minutes integer,
    total_work_minutes integer,
    CONSTRAINT employee_shifts_status_check CHECK (((status)::text = ANY (ARRAY[('SCHEDULED'::character varying)::text, ('CHECKED_IN'::character varying)::text, ('ON_BREAK'::character varying)::text, ('SUSPENDED'::character varying)::text, ('CHECKED_OUT'::character varying)::text, ('ABSENT'::character varying)::text, ('CANCELLED'::character varying)::text, ('COMPLETED'::character varying)::text])))
);


--
-- Name: employee_targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_targets (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    achieved boolean,
    actual_value integer,
    date date NOT NULL,
    employee_id uuid NOT NULL,
    metric character varying(255) NOT NULL,
    notes character varying(255),
    target_value integer NOT NULL,
    CONSTRAINT employee_targets_metric_check CHECK (((metric)::text = ANY (ARRAY[('ORDERS'::character varying)::text, ('ITEMS'::character varying)::text, ('REVENUE'::character varying)::text, ('QUALITY'::character varying)::text, ('CUSTOMER_SATISFACTION'::character varying)::text])))
);


--
-- Name: expenses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expenses (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    amount numeric(12,2) NOT NULL,
    category character varying(30) NOT NULL,
    description character varying(255) NOT NULL,
    expense_date date NOT NULL,
    notes text,
    paid_to character varying(255),
    reference character varying(255),
    CONSTRAINT expenses_category_check CHECK (((category)::text = ANY (ARRAY[('SUPPLIES'::character varying)::text, ('UTILITIES'::character varying)::text, ('WAGES'::character varying)::text, ('RENT'::character varying)::text, ('EQUIPMENT'::character varying)::text, ('MARKETING'::character varying)::text, ('REPAIRS'::character varying)::text, ('INSURANCE'::character varying)::text, ('TAXES'::character varying)::text, ('TRANSPORT'::character varying)::text, ('OTHER'::character varying)::text])))
);


--
-- Name: garment_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.garment_types (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    category character varying(255),
    description character varying(255),
    icon character varying(255),
    is_active boolean,
    name character varying(255) NOT NULL,
    sort_order integer
);


--
-- Name: inventory_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    category character varying(255) NOT NULL,
    current_stock numeric(10,2),
    description character varying(255),
    is_active boolean,
    location character varying(255),
    max_stock_level numeric(10,2),
    min_stock_level numeric(10,2),
    name character varying(255) NOT NULL,
    reorder_level integer NOT NULL,
    reorder_quantity integer NOT NULL,
    sku character varying(255) NOT NULL,
    supplier_id uuid,
    unit character varying(255) NOT NULL,
    unit_price numeric(10,2),
    CONSTRAINT inventory_items_category_check CHECK (((category)::text = ANY (ARRAY[('DETERGENT'::character varying)::text, ('SOFTENER'::character varying)::text, ('BLEACH'::character varying)::text, ('STAIN_REMOVAL'::character varying)::text, ('PACKAGING'::character varying)::text, ('HANGER'::character varying)::text, ('TAG'::character varying)::text, ('LABEL'::character varying)::text, ('GLOVE'::character varying)::text, ('MASK'::character varying)::text, ('CLEANING_SUPPLY'::character varying)::text, ('OTHER'::character varying)::text]))),
    CONSTRAINT inventory_items_unit_check CHECK (((unit)::text = ANY (ARRAY[('LITER'::character varying)::text, ('MILLILITER'::character varying)::text, ('KILOGRAM'::character varying)::text, ('GRAM'::character varying)::text, ('PIECE'::character varying)::text, ('BOX'::character varying)::text, ('CASE'::character varying)::text, ('BOTTLE'::character varying)::text, ('BAG'::character varying)::text, ('ROLL'::character varying)::text])))
);


--
-- Name: invoice_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(10,2),
    order_date timestamp(6) without time zone,
    order_id uuid,
    order_number character varying(255),
    status character varying(255),
    invoice_id uuid NOT NULL
);


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    account_id uuid NOT NULL,
    company_name character varying(255),
    due_date date,
    invoice_number character varying(255) NOT NULL,
    paid_at timestamp(6) without time zone,
    pdf_url character varying(255),
    period_end date,
    period_start date,
    sent_at timestamp(6) without time zone,
    status character varying(255),
    subtotal numeric(10,2),
    tax numeric(10,2),
    total numeric(10,2)
);


--
-- Name: item_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_images (
    item_id uuid NOT NULL,
    image_url character varying(255)
);


--
-- Name: item_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    notes character varying(255),
    status character varying(255) NOT NULL,
    "timestamp" timestamp(6) without time zone NOT NULL,
    item_id uuid NOT NULL
);


--
-- Name: item_worker_interactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_worker_interactions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    first_access_method character varying(255),
    first_interaction timestamp(6) without time zone NOT NULL,
    interaction_count integer,
    item_id uuid NOT NULL,
    last_interaction timestamp(6) without time zone NOT NULL,
    worker_id uuid NOT NULL
);


--
-- Name: loyalty_rewards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_rewards (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    description character varying(255),
    expires_at timestamp(6) without time zone,
    is_active boolean,
    name character varying(255) NOT NULL,
    points_cost integer NOT NULL
);


--
-- Name: loyalty_tiers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_tiers (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    benefits text[],
    level integer NOT NULL,
    name character varying(255) NOT NULL,
    points_required integer NOT NULL
);


--
-- Name: loyalty_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_transactions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    balance integer NOT NULL,
    description character varying(255),
    points integer NOT NULL,
    source character varying(255) NOT NULL,
    source_id uuid,
    customer_id uuid NOT NULL
);


--
-- Name: notification_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_deliveries (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    attempt_count integer NOT NULL,
    channel character varying(255) NOT NULL,
    delivered_at timestamp(6) without time zone,
    device_id uuid,
    last_error text,
    last_error_type character varying(255),
    max_attempts integer NOT NULL,
    next_retry_at timestamp(6) without time zone,
    notification_id uuid NOT NULL,
    recipient character varying(255),
    sent_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    CONSTRAINT notification_deliveries_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT notification_deliveries_last_error_type_check CHECK (((last_error_type)::text = ANY (ARRAY[('TRANSIENT'::character varying)::text, ('PERMANENT'::character varying)::text]))),
    CONSTRAINT notification_deliveries_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SENT'::character varying)::text, ('FAILED'::character varying)::text, ('EXHAUSTED'::character varying)::text])))
);


--
-- Name: notification_delivery_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_events (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    delivery_id uuid NOT NULL,
    detail character varying(255),
    event_type character varying(255) NOT NULL,
    occurred_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT notification_delivery_events_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('SENT'::character varying)::text, ('DELIVERED'::character varying)::text, ('OPENED'::character varying)::text, ('CLICKED'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: notification_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_logs (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    channel character varying(255) NOT NULL,
    content character varying(5000),
    delivered_at timestamp(6) without time zone,
    error_message character varying(255),
    provider_response character varying(255),
    recipient character varying(255) NOT NULL,
    retry_count integer,
    sent_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    subject character varying(255),
    notification_id uuid,
    CONSTRAINT notification_logs_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT notification_logs_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SENT'::character varying)::text, ('DELIVERED'::character varying)::text, ('FAILED'::character varying)::text, ('BOUNCED'::character varying)::text, ('COMPLAINT'::character varying)::text])))
);


--
-- Name: notification_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_outbox (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    attempt_count integer NOT NULL,
    body character varying(255),
    channel character varying(255),
    data jsonb,
    last_error text,
    locked_at timestamp(6) without time zone,
    locked_by uuid,
    mandatory boolean NOT NULL,
    max_attempts integer NOT NULL,
    next_attempt_at timestamp(6) without time zone,
    priority character varying(255),
    processed_at timestamp(6) without time zone,
    scheduled_for timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    template_id uuid,
    title character varying(255),
    type character varying(255) NOT NULL,
    user_id uuid,
    CONSTRAINT notification_outbox_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT notification_outbox_priority_check CHECK (((priority)::text = ANY (ARRAY[('LOW'::character varying)::text, ('NORMAL'::character varying)::text, ('HIGH'::character varying)::text, ('URGENT'::character varying)::text]))),
    CONSTRAINT notification_outbox_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PROCESSED'::character varying)::text, ('DEAD'::character varying)::text]))),
    CONSTRAINT notification_outbox_type_check CHECK (((type)::text = ANY (ARRAY[('ORDER_STATUS'::character varying)::text, ('PAYMENT'::character varying)::text, ('REMINDER'::character varying)::text, ('PROMOTION'::character varying)::text, ('ALERT'::character varying)::text, ('SYSTEM'::character varying)::text, ('STOCK_REQUEST'::character varying)::text])))
);


--
-- Name: notification_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_templates (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    body_template character varying(5000) NOT NULL,
    channel character varying(255) NOT NULL,
    description character varying(255),
    is_active boolean,
    is_mandatory boolean NOT NULL,
    name character varying(255) NOT NULL,
    subject character varying(255),
    title_template character varying(255),
    type character varying(255) NOT NULL,
    variables text[],
    CONSTRAINT notification_templates_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT notification_templates_type_check CHECK (((type)::text = ANY (ARRAY[('ORDER_STATUS'::character varying)::text, ('PAYMENT'::character varying)::text, ('REMINDER'::character varying)::text, ('PROMOTION'::character varying)::text, ('ALERT'::character varying)::text, ('SYSTEM'::character varying)::text, ('STOCK_REQUEST'::character varying)::text])))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    body character varying(2000) NOT NULL,
    channel character varying(255) NOT NULL,
    data jsonb,
    delivered_at timestamp(6) without time zone,
    error_message character varying(255),
    priority character varying(255),
    read_at timestamp(6) without time zone,
    retry_count integer,
    scheduled_for timestamp(6) without time zone,
    sent_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    template_id uuid,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    user_id uuid,
    CONSTRAINT notifications_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT notifications_priority_check CHECK (((priority)::text = ANY (ARRAY[('LOW'::character varying)::text, ('NORMAL'::character varying)::text, ('HIGH'::character varying)::text, ('URGENT'::character varying)::text]))),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SENT'::character varying)::text, ('DELIVERED'::character varying)::text, ('FAILED'::character varying)::text, ('READ'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY (ARRAY[('ORDER_STATUS'::character varying)::text, ('PAYMENT'::character varying)::text, ('REMINDER'::character varying)::text, ('PROMOTION'::character varying)::text, ('ALERT'::character varying)::text, ('SYSTEM'::character varying)::text, ('STOCK_REQUEST'::character varying)::text])))
);


--
-- Name: order_discrepancies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_discrepancies (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    description text NOT NULL,
    handled_by uuid,
    handling_note text,
    order_item_id uuid,
    reported_by uuid NOT NULL,
    resolved_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT order_discrepancies_status_check CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('ACKNOWLEDGED'::character varying)::text, ('RESOLVED'::character varying)::text, ('DISMISSED'::character varying)::text]))),
    CONSTRAINT order_discrepancies_type_check CHECK (((type)::text = ANY (ARRAY[('CODE_MISMATCH'::character varying)::text, ('COUNT_MISMATCH'::character varying)::text, ('DAMAGED'::character varying)::text, ('MISSING_ITEM'::character varying)::text, ('OTHER'::character varying)::text])))
);


--
-- Name: order_item_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_item_units (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    barcode character varying(30),
    notes character varying(255),
    status character varying(50),
    unit_number integer NOT NULL,
    order_item_id uuid NOT NULL
);


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    barcode character varying(255),
    description character varying(255),
    discount numeric(10,2),
    garment_type character varying(255),
    item_number character varying(255) NOT NULL,
    quantity integer NOT NULL,
    requires_washing boolean NOT NULL,
    service_type character varying(255) NOT NULL,
    service_type_id uuid,
    special_instructions character varying(255),
    status character varying(255),
    subtotal numeric(10,2),
    total numeric(10,2),
    unit_price numeric(10,2),
    weight numeric(10,2),
    order_id uuid NOT NULL,
    CONSTRAINT order_items_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RECEIVED'::character varying)::text, ('WASHING'::character varying)::text, ('WASHED'::character varying)::text, ('IRONING'::character varying)::text, ('IRONED'::character varying)::text, ('QUALITY_CHECK'::character varying)::text, ('COMPLETED'::character varying)::text, ('ISSUE_REPORTED'::character varying)::text])))
);


--
-- Name: order_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_notes (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    content character varying(2000) NOT NULL,
    is_customer_visible boolean,
    type character varying(255),
    order_id uuid NOT NULL
);


--
-- Name: order_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_payments (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    amount numeric(10,2) NOT NULL,
    change_amount numeric(10,2),
    collected_by uuid,
    customer_id uuid,
    metadata jsonb,
    method character varying(255) NOT NULL,
    order_id uuid NOT NULL,
    paid_at timestamp(6) without time zone NOT NULL,
    reference character varying(255),
    status character varying(255),
    tip numeric(10,2),
    provider character varying(32),
    provider_reference character varying(255),
    provider_status character varying(64)
);


--
-- Name: order_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_tags (
    order_id uuid NOT NULL,
    tag character varying(255)
);


--
-- Name: order_timeline; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_timeline (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    description character varying(255),
    metadata jsonb,
    status character varying(255),
    "timestamp" timestamp(6) without time zone NOT NULL,
    type character varying(255) NOT NULL,
    user_id uuid,
    user_name character varying(255),
    order_id uuid NOT NULL
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    actual_ready_at timestamp(6) without time zone,
    balance_due numeric(10,2),
    completed_at timestamp(6) without time zone,
    customer_id uuid NOT NULL,
    delivery_address_id uuid,
    delivered_at timestamp(6) without time zone,
    delivered_by uuid,
    delivery_notes character varying(255),
    proof_of_delivery character varying(255),
    recipient_name character varying(255),
    scheduled_time timestamp(6) without time zone,
    delivery_type character varying(255),
    expected_ready_at timestamp(6) without time zone,
    invoice_id uuid,
    item_count integer,
    order_number character varying(255) NOT NULL,
    paid_amount numeric(10,2),
    priority character varying(255),
    promised_date timestamp(6) without time zone,
    received_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    total_amount numeric(10,2),
    tracking_number character varying(255) NOT NULL,
    CONSTRAINT orders_priority_check CHECK (((priority)::text = ANY (ARRAY[('NORMAL'::character varying)::text, ('EXPRESS'::character varying)::text, ('URGENT'::character varying)::text]))),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('RECEIVED'::character varying)::text, ('WASHING'::character varying)::text, ('WASHED'::character varying)::text, ('IRONING'::character varying)::text, ('IRONED'::character varying)::text, ('QUALITY_CHECK'::character varying)::text, ('READY_FOR_PICKUP'::character varying)::text, ('OUT_FOR_DELIVERY'::character varying)::text, ('COMPLETED'::character varying)::text, ('RETURNED'::character varying)::text, ('CANCELLED'::character varying)::text, ('ARCHIVED'::character varying)::text])))
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    expires_at timestamp(6) without time zone NOT NULL,
    token_hash character varying(255) NOT NULL,
    used_at timestamp(6) without time zone,
    user_id uuid NOT NULL
);


--
-- Name: payment_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_methods (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    icon character varying(255),
    is_active boolean,
    max_amount numeric(10,2),
    min_amount numeric(10,2),
    name character varying(255) NOT NULL,
    requires_reference boolean,
    surcharge numeric(5,2),
    type character varying(255) NOT NULL
);


--
-- Name: payment_provider_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_provider_configs (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    provider character varying(32) NOT NULL,
    enabled boolean NOT NULL,
    connection_mode character varying(16),
    credentials_encrypted text,
    platform_subaccount_id character varying(64),
    CONSTRAINT payment_provider_configs_connection_mode_check CHECK (((connection_mode)::text = ANY ((ARRAY['DISCONNECTED'::character varying, 'PLATFORM'::character varying, 'BYO'::character varying])::text[])))
);


--
-- Name: payment_reconciliation_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_reconciliation_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(12,2) NOT NULL,
    business_id uuid,
    notes character varying(255),
    order_id uuid,
    provider character varying(32) NOT NULL,
    provider_reference character varying(255) NOT NULL,
    raw_event text,
    received_at timestamp(6) without time zone NOT NULL,
    resolved_at timestamp(6) without time zone,
    resolved_by uuid,
    status character varying(16) NOT NULL
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    category character varying(255),
    description character varying(255),
    display_name character varying(255),
    is_default boolean,
    name character varying(255) NOT NULL,
    scope character varying(255),
    CONSTRAINT permissions_scope_check CHECK (((scope)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('BUSINESS'::character varying)::text, ('SHOP'::character varying)::text])))
);


--
-- Name: platform_payment_provider_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_payment_provider_configs (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    platform_enabled boolean NOT NULL,
    provider character varying(32) NOT NULL,
    updated_by_username character varying(255)
);


--
-- Name: purchase_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_items (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    notes character varying(255),
    quantity integer NOT NULL,
    received_quantity integer,
    total numeric(10,2),
    unit_price numeric(10,2) NOT NULL,
    item_id uuid NOT NULL,
    po_id uuid NOT NULL
);


--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_orders (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    approved_at timestamp(6) without time zone,
    approved_by uuid,
    delivered_at timestamp(6) without time zone,
    delivered_by uuid,
    expected_delivery date,
    notes character varying(255),
    order_date timestamp(6) without time zone NOT NULL,
    po_number character varying(255) NOT NULL,
    shipping numeric(10,2),
    status character varying(255) NOT NULL,
    subtotal numeric(10,2),
    tax numeric(10,2),
    terms character varying(255),
    total numeric(10,2),
    supplier_id uuid NOT NULL,
    CONSTRAINT purchase_orders_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('SENT'::character varying)::text, ('CONFIRMED'::character varying)::text, ('SHIPPED'::character varying)::text, ('PARTIALLY_RECEIVED'::character varying)::text, ('RECEIVED'::character varying)::text, ('CANCELLED'::character varying)::text, ('REJECTED'::character varying)::text, ('APPROVED'::character varying)::text])))
);


--
-- Name: push_configurations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.push_configurations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    apns_bundle_id character varying(255),
    apns_key_id character varying(255),
    apns_private_key_encrypted character varying(255),
    apns_team_id character varying(255),
    fcm_server_key_encrypted character varying(255),
    is_configured boolean
);


--
-- Name: quality_checklists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_checklists (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    garment_type_id uuid,
    is_active boolean,
    name character varying(255) NOT NULL,
    service_type_id uuid
);


--
-- Name: quality_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_checks (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    checked_at timestamp(6) without time zone,
    checked_by uuid,
    checklist_id uuid,
    notes character varying(255),
    order_item_id uuid NOT NULL,
    status character varying(255)
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    device_info character varying(255),
    expires_at timestamp(6) without time zone NOT NULL,
    revoked_at timestamp(6) without time zone,
    token_hash character varying(255) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refunds (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    amount numeric(10,2) NOT NULL,
    approval_code character varying(255),
    business_id uuid NOT NULL,
    method character varying(255),
    notes character varying(255),
    processed_at timestamp(6) without time zone,
    processed_by uuid,
    reason character varying(255) NOT NULL,
    reason_code character varying(255),
    status character varying(255),
    payment_id uuid NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid,
    description character varying(255),
    is_system boolean,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT roles_type_check CHECK (((type)::text = ANY (ARRAY[('PLATFORM'::character varying)::text, ('BUSINESS'::character varying)::text, ('CUSTOM'::character varying)::text])))
);


--
-- Name: security_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_events (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    block_reason character varying(255),
    blocked boolean,
    details jsonb,
    device_fingerprint character varying(255),
    event_type character varying(255) NOT NULL,
    ip_address character varying(255),
    location character varying(255),
    resolved_at timestamp(6) without time zone,
    resolved_by uuid,
    severity character varying(255),
    "timestamp" timestamp(6) without time zone NOT NULL,
    user_agent character varying(255),
    user_id uuid,
    username character varying(255),
    CONSTRAINT security_events_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('LOGIN_SUCCESS'::character varying)::text, ('LOGIN_FAILED'::character varying)::text, ('LOGOUT'::character varying)::text, ('PASSWORD_CHANGE'::character varying)::text, ('PASSWORD_RESET'::character varying)::text, ('TWO_FACTOR_ENABLED'::character varying)::text, ('TWO_FACTOR_DISABLED'::character varying)::text, ('TWO_FACTOR_FAILED'::character varying)::text, ('PERMISSION_CHANGE'::character varying)::text, ('ROLE_CHANGE'::character varying)::text, ('USER_LOCKED'::character varying)::text, ('USER_UNLOCKED'::character varying)::text, ('SUSPICIOUS_ACTIVITY'::character varying)::text, ('BRUTE_FORCE_ATTEMPT'::character varying)::text, ('API_ABUSE'::character varying)::text, ('DATA_EXPORT'::character varying)::text, ('DATA_DELETE'::character varying)::text, ('CONSENT_CHANGE'::character varying)::text]))),
    CONSTRAINT security_events_severity_check CHECK (((severity)::text = ANY (ARRAY[('INFO'::character varying)::text, ('WARNING'::character varying)::text, ('ERROR'::character varying)::text, ('CRITICAL'::character varying)::text])))
);


--
-- Name: service_garment_pricing; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_garment_pricing (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    garment_type_id uuid NOT NULL,
    is_active boolean,
    price numeric(10,2) NOT NULL,
    service_type_id uuid NOT NULL
);


--
-- Name: service_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_types (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    category character varying(255),
    default_price numeric(10,2),
    description character varying(255),
    icon character varying(255),
    is_active boolean,
    name character varying(255) NOT NULL,
    sort_order integer,
    unit character varying(255)
);


--
-- Name: shop_operating_hours; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_operating_hours (
    shop_id uuid NOT NULL,
    close_time character varying(255),
    closed boolean,
    day character varying(255),
    open_time character varying(255),
    CONSTRAINT shop_operating_hours_day_check CHECK (((day)::text = ANY (ARRAY[('MONDAY'::character varying)::text, ('TUESDAY'::character varying)::text, ('WEDNESDAY'::character varying)::text, ('THURSDAY'::character varying)::text, ('FRIDAY'::character varying)::text, ('SATURDAY'::character varying)::text, ('SUNDAY'::character varying)::text])))
);


--
-- Name: shop_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_stock (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    last_counted timestamp(6) without time zone,
    last_restocked timestamp(6) without time zone,
    location_details character varying(255),
    maximum_quantity numeric(10,2),
    minimum_quantity numeric(10,2),
    quantity numeric(10,2) NOT NULL,
    reorder_point numeric(10,2),
    status character varying(255),
    item_id uuid NOT NULL,
    CONSTRAINT shop_stock_status_check CHECK (((status)::text = ANY (ARRAY[('NORMAL'::character varying)::text, ('LOW'::character varying)::text, ('CRITICAL'::character varying)::text, ('OUT_OF_STOCK'::character varying)::text, ('OVERSTOCKED'::character varying)::text])))
);


--
-- Name: shops; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shops (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    active boolean,
    address_line1 character varying(255),
    address_line2 character varying(255),
    city character varying(255),
    country character varying(255),
    latitude double precision,
    longitude double precision,
    postal_code character varying(255),
    state character varying(255),
    code character varying(255) NOT NULL,
    email character varying(255),
    name character varying(255) NOT NULL,
    phone character varying(255),
    timezone character varying(255)
);


--
-- Name: sms_configurations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sms_configurations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    account_sid_encrypted character varying(255),
    auth_token_encrypted character varying(255),
    from_number character varying(255),
    is_configured boolean,
    provider character varying(255),
    CONSTRAINT sms_configurations_provider_check CHECK (((provider)::text = ANY (ARRAY[('TWILIO'::character varying)::text, ('AWS_SNS'::character varying)::text, ('VONAGE'::character varying)::text, ('MESSAGEBIRD'::character varying)::text])))
);


--
-- Name: stock_alerts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_alerts (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    acknowledged_at timestamp(6) without time zone,
    acknowledged_by uuid,
    current_stock numeric(10,2) NOT NULL,
    message character varying(255),
    reorder_level integer NOT NULL,
    resolved_at timestamp(6) without time zone,
    resolved_by uuid,
    severity character varying(255),
    status character varying(255) NOT NULL,
    suggested_order integer,
    item_id uuid NOT NULL,
    CONSTRAINT stock_alerts_severity_check CHECK (((severity)::text = ANY (ARRAY[('INFO'::character varying)::text, ('WARNING'::character varying)::text, ('CRITICAL'::character varying)::text]))),
    CONSTRAINT stock_alerts_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('ACKNOWLEDGED'::character varying)::text, ('RESOLVED'::character varying)::text, ('IGNORED'::character varying)::text])))
);


--
-- Name: stock_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_requests (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    notes character varying(255),
    quantity numeric(10,2) NOT NULL,
    requested_by uuid NOT NULL,
    resolution_note character varying(255),
    reviewed_at timestamp(6) without time zone,
    reviewed_by uuid,
    status character varying(255) NOT NULL,
    urgency character varying(255) NOT NULL,
    item_id uuid NOT NULL,
    CONSTRAINT stock_requests_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text, ('FULFILLED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT stock_requests_urgency_check CHECK (((urgency)::text = ANY (ARRAY[('LOW'::character varying)::text, ('NORMAL'::character varying)::text, ('HIGH'::character varying)::text])))
);


--
-- Name: stock_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_transactions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    after_quantity numeric(10,2),
    before_quantity numeric(10,2),
    notes character varying(255),
    order_id uuid,
    order_item_id uuid,
    performed_by uuid,
    quantity numeric(10,2) NOT NULL,
    reason character varying(255) NOT NULL,
    reference character varying(255),
    total_cost numeric(10,2),
    transaction_date timestamp(6) without time zone NOT NULL,
    type character varying(255) NOT NULL,
    unit_cost numeric(10,2),
    item_id uuid NOT NULL,
    CONSTRAINT stock_transactions_type_check CHECK (((type)::text = ANY (ARRAY[('RECEIVED'::character varying)::text, ('USED'::character varying)::text, ('WASTED'::character varying)::text, ('RETURNED'::character varying)::text, ('TRANSFER_IN'::character varying)::text, ('TRANSFER_OUT'::character varying)::text, ('ADJUSTMENT'::character varying)::text, ('COUNTED'::character varying)::text])))
);


--
-- Name: supplier_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplier_categories (
    supplier_id uuid NOT NULL,
    category character varying(255)
);


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    address_line1 character varying(255),
    address_line2 character varying(255),
    city character varying(255),
    country character varying(255),
    postal_code character varying(255),
    state character varying(255),
    contact_person character varying(255),
    email character varying(255),
    is_active boolean,
    lead_time_days integer,
    minimum_order_amount numeric(10,2),
    name character varying(255) NOT NULL,
    notes character varying(255),
    payment_terms character varying(255),
    phone character varying(255),
    rating numeric(2,1),
    shipping_cost numeric(10,2),
    tax_id character varying(255),
    website character varying(255)
);


--
-- Name: system_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_settings (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    description character varying(255),
    setting_key character varying(100) NOT NULL,
    setting_value text,
    updated_by_username character varying(255)
);


--
-- Name: time_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.time_entries (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    device_id character varying(255),
    employee_id uuid NOT NULL,
    event_type character varying(255) NOT NULL,
    ip_address character varying(255),
    location character varying(255),
    notes character varying(255),
    shift_id uuid,
    "timestamp" timestamp(6) without time zone NOT NULL,
    CONSTRAINT time_entries_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('CLOCK_IN'::character varying)::text, ('CLOCK_OUT'::character varying)::text, ('BREAK_START'::character varying)::text, ('BREAK_END'::character varying)::text, ('SUSPEND'::character varying)::text, ('RESUME'::character varying)::text, ('AUTO_CLOSED'::character varying)::text])))
);


--
-- Name: user_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_devices (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    shop_id uuid,
    app_version character varying(255),
    device_id character varying(255),
    device_type character varying(255),
    is_active boolean,
    last_used_at timestamp(6) without time zone,
    model character varying(255),
    os_version character varying(255),
    push_token character varying(255),
    user_id uuid NOT NULL,
    CONSTRAINT user_devices_device_type_check CHECK (((device_type)::text = ANY (ARRAY[('ANDROID'::character varying)::text, ('IOS'::character varying)::text, ('WEB'::character varying)::text, ('TABLET'::character varying)::text, ('DESKTOP'::character varying)::text])))
);


--
-- Name: user_notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_notification_preferences (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid NOT NULL,
    channel character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    notification_type character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT user_notification_preferences_channel_check CHECK (((channel)::text = ANY (ARRAY[('IN_APP'::character varying)::text, ('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('WHATSAPP'::character varying)::text, ('PUSH'::character varying)::text]))),
    CONSTRAINT user_notification_preferences_notification_type_check CHECK (((notification_type)::text = ANY (ARRAY[('ORDER_STATUS'::character varying)::text, ('PAYMENT'::character varying)::text, ('REMINDER'::character varying)::text, ('PROMOTION'::character varying)::text, ('ALERT'::character varying)::text, ('SYSTEM'::character varying)::text, ('STOCK_REQUEST'::character varying)::text])))
);


--
-- Name: user_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_permissions (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid,
    expires_at timestamp(6) without time zone,
    granted_at timestamp(6) without time zone,
    granted_by uuid,
    shop_id uuid,
    permission_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid,
    shop_id uuid NOT NULL,
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: user_shops; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_shops (
    user_id uuid NOT NULL,
    shop_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    updated_at timestamp(6) without time zone,
    updated_by uuid,
    version bigint,
    business_id uuid,
    shop_id uuid,
    email character varying(255),
    enabled boolean NOT NULL,
    first_name character varying(255) NOT NULL,
    last_login timestamp(6) without time zone,
    last_name character varying(255) NOT NULL,
    phone character varying(255),
    primary_shop_id uuid,
    profile_image character varying(255),
    username character varying(255) NOT NULL
);


--
-- Name: attendance attendance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT attendance_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: auth_credentials auth_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_credentials
    ADD CONSTRAINT auth_credentials_pkey PRIMARY KEY (id);


--
-- Name: billing_coupon_redemptions billing_coupon_redemptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_coupon_redemptions
    ADD CONSTRAINT billing_coupon_redemptions_pkey PRIMARY KEY (id);


--
-- Name: billing_coupons billing_coupons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_coupons
    ADD CONSTRAINT billing_coupons_pkey PRIMARY KEY (id);


--
-- Name: billing_invoice_counters billing_invoice_counters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoice_counters
    ADD CONSTRAINT billing_invoice_counters_pkey PRIMARY KEY (counter_year);


--
-- Name: billing_invoice_line_items billing_invoice_line_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoice_line_items
    ADD CONSTRAINT billing_invoice_line_items_pkey PRIMARY KEY (id);


--
-- Name: billing_invoices billing_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT billing_invoices_pkey PRIMARY KEY (id);


--
-- Name: billing_payment_methods billing_payment_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_payment_methods
    ADD CONSTRAINT billing_payment_methods_pkey PRIMARY KEY (id);


--
-- Name: billing_payments billing_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_payments
    ADD CONSTRAINT billing_payments_pkey PRIMARY KEY (id);


--
-- Name: billing_plan_features billing_plan_features_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_features
    ADD CONSTRAINT billing_plan_features_pkey PRIMARY KEY (id);


--
-- Name: billing_plan_versions billing_plan_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_versions
    ADD CONSTRAINT billing_plan_versions_pkey PRIMARY KEY (id);


--
-- Name: billing_plans billing_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plans
    ADD CONSTRAINT billing_plans_pkey PRIMARY KEY (id);


--
-- Name: billing_subscription_coupons billing_subscription_coupons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_coupons
    ADD CONSTRAINT billing_subscription_coupons_pkey PRIMARY KEY (coupon_id, subscription_id);


--
-- Name: billing_subscription_events billing_subscription_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_events
    ADD CONSTRAINT billing_subscription_events_pkey PRIMARY KEY (id);


--
-- Name: billing_subscription_features billing_subscription_features_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_features
    ADD CONSTRAINT billing_subscription_features_pkey PRIMARY KEY (id);


--
-- Name: billing_subscriptions billing_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscriptions
    ADD CONSTRAINT billing_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: billing_usage_records billing_usage_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_usage_records
    ADD CONSTRAINT billing_usage_records_pkey PRIMARY KEY (id);


--
-- Name: business_config business_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_config
    ADD CONSTRAINT business_config_pkey PRIMARY KEY (id);


--
-- Name: businesses businesses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.businesses
    ADD CONSTRAINT businesses_pkey PRIMARY KEY (id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_pkey PRIMARY KEY (id);


--
-- Name: check_results check_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_results
    ADD CONSTRAINT check_results_pkey PRIMARY KEY (id);


--
-- Name: checklist_items checklist_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_items
    ADD CONSTRAINT checklist_items_pkey PRIMARY KEY (id);


--
-- Name: compliance_reports compliance_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compliance_reports
    ADD CONSTRAINT compliance_reports_pkey PRIMARY KEY (id);


--
-- Name: consent_records consent_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_records
    ADD CONSTRAINT consent_records_pkey PRIMARY KEY (id);


--
-- Name: corporate_accounts corporate_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corporate_accounts
    ADD CONSTRAINT corporate_accounts_pkey PRIMARY KEY (id);


--
-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (id);


--
-- Name: customer_notes customer_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_notes
    ADD CONSTRAINT customer_notes_pkey PRIMARY KEY (id);


--
-- Name: customer_preferences customer_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferences
    ADD CONSTRAINT customer_preferences_pkey PRIMARY KEY (id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: data_retention_policies data_retention_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_retention_policies
    ADD CONSTRAINT data_retention_policies_pkey PRIMARY KEY (id);


--
-- Name: data_subject_requests data_subject_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_subject_requests
    ADD CONSTRAINT data_subject_requests_pkey PRIMARY KEY (id);


--
-- Name: defects defects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defects
    ADD CONSTRAINT defects_pkey PRIMARY KEY (id);


--
-- Name: email_configurations email_configurations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_configurations
    ADD CONSTRAINT email_configurations_pkey PRIMARY KEY (id);


--
-- Name: employee_performance employee_performance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_performance
    ADD CONSTRAINT employee_performance_pkey PRIMARY KEY (id);


--
-- Name: employee_schedules employee_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedules
    ADD CONSTRAINT employee_schedules_pkey PRIMARY KEY (id);


--
-- Name: employee_shifts employee_shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_shifts
    ADD CONSTRAINT employee_shifts_pkey PRIMARY KEY (id);


--
-- Name: employee_targets employee_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_targets
    ADD CONSTRAINT employee_targets_pkey PRIMARY KEY (id);


--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);


--
-- Name: garment_types garment_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.garment_types
    ADD CONSTRAINT garment_types_pkey PRIMARY KEY (id);


--
-- Name: businesses idx_business_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.businesses
    ADD CONSTRAINT idx_business_slug UNIQUE (slug);


--
-- Name: business_config idx_config_business_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_config
    ADD CONSTRAINT idx_config_business_id UNIQUE (business_id);


--
-- Name: email_configurations idx_email_business; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_configurations
    ADD CONSTRAINT idx_email_business UNIQUE (business_id);


--
-- Name: inventory_items idx_inventory_sku; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT idx_inventory_sku UNIQUE (sku);


--
-- Name: item_worker_interactions idx_iwi_worker_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_worker_interactions
    ADD CONSTRAINT idx_iwi_worker_item UNIQUE (worker_id, item_id);


--
-- Name: orders idx_order_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT idx_order_number UNIQUE (order_number);


--
-- Name: orders idx_order_tracking; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT idx_order_tracking UNIQUE (tracking_number);


--
-- Name: purchase_orders idx_po_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT idx_po_number UNIQUE (po_number);


--
-- Name: service_garment_pricing idx_pricing_combo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_garment_pricing
    ADD CONSTRAINT idx_pricing_combo UNIQUE (service_type_id, garment_type_id);


--
-- Name: push_configurations idx_push_business; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_configurations
    ADD CONSTRAINT idx_push_business UNIQUE (business_id);


--
-- Name: shops idx_shop_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shops
    ADD CONSTRAINT idx_shop_code UNIQUE (code);


--
-- Name: sms_configurations idx_sms_business; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_configurations
    ADD CONSTRAINT idx_sms_business UNIQUE (business_id);


--
-- Name: inventory_items inventory_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT inventory_items_pkey PRIMARY KEY (id);


--
-- Name: invoice_items invoice_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: item_status_history item_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_status_history
    ADD CONSTRAINT item_status_history_pkey PRIMARY KEY (id);


--
-- Name: item_worker_interactions item_worker_interactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_worker_interactions
    ADD CONSTRAINT item_worker_interactions_pkey PRIMARY KEY (id);


--
-- Name: loyalty_rewards loyalty_rewards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_rewards
    ADD CONSTRAINT loyalty_rewards_pkey PRIMARY KEY (id);


--
-- Name: loyalty_tiers loyalty_tiers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_tiers
    ADD CONSTRAINT loyalty_tiers_pkey PRIMARY KEY (id);


--
-- Name: loyalty_transactions loyalty_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_transactions
    ADD CONSTRAINT loyalty_transactions_pkey PRIMARY KEY (id);


--
-- Name: notification_deliveries notification_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_deliveries
    ADD CONSTRAINT notification_deliveries_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_events notification_delivery_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_events
    ADD CONSTRAINT notification_delivery_events_pkey PRIMARY KEY (id);


--
-- Name: notification_logs notification_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_logs
    ADD CONSTRAINT notification_logs_pkey PRIMARY KEY (id);


--
-- Name: notification_outbox notification_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_outbox
    ADD CONSTRAINT notification_outbox_pkey PRIMARY KEY (id);


--
-- Name: notification_templates notification_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: order_discrepancies order_discrepancies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_discrepancies
    ADD CONSTRAINT order_discrepancies_pkey PRIMARY KEY (id);


--
-- Name: order_item_units order_item_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item_units
    ADD CONSTRAINT order_item_units_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: order_notes order_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_notes
    ADD CONSTRAINT order_notes_pkey PRIMARY KEY (id);


--
-- Name: order_payments order_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_payments
    ADD CONSTRAINT order_payments_pkey PRIMARY KEY (id);


--
-- Name: order_timeline order_timeline_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_timeline
    ADD CONSTRAINT order_timeline_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: payment_methods payment_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);


--
-- Name: payment_provider_configs payment_provider_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_provider_configs
    ADD CONSTRAINT payment_provider_configs_pkey PRIMARY KEY (id);


--
-- Name: payment_reconciliation_items payment_reconciliation_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_reconciliation_items
    ADD CONSTRAINT payment_reconciliation_items_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: platform_payment_provider_configs platform_payment_provider_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_payment_provider_configs
    ADD CONSTRAINT platform_payment_provider_configs_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_items purchase_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);


--
-- Name: push_configurations push_configurations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_configurations
    ADD CONSTRAINT push_configurations_pkey PRIMARY KEY (id);


--
-- Name: quality_checklists quality_checklists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_checklists
    ADD CONSTRAINT quality_checklists_pkey PRIMARY KEY (id);


--
-- Name: quality_checks quality_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_checks
    ADD CONSTRAINT quality_checks_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refunds refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: security_events security_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_pkey PRIMARY KEY (id);


--
-- Name: service_garment_pricing service_garment_pricing_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_garment_pricing
    ADD CONSTRAINT service_garment_pricing_pkey PRIMARY KEY (id);


--
-- Name: service_types service_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_types
    ADD CONSTRAINT service_types_pkey PRIMARY KEY (id);


--
-- Name: shop_stock shop_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_stock
    ADD CONSTRAINT shop_stock_pkey PRIMARY KEY (id);


--
-- Name: shops shops_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shops
    ADD CONSTRAINT shops_pkey PRIMARY KEY (id);


--
-- Name: sms_configurations sms_configurations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_configurations
    ADD CONSTRAINT sms_configurations_pkey PRIMARY KEY (id);


--
-- Name: stock_alerts stock_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_alerts
    ADD CONSTRAINT stock_alerts_pkey PRIMARY KEY (id);


--
-- Name: stock_requests stock_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_requests
    ADD CONSTRAINT stock_requests_pkey PRIMARY KEY (id);


--
-- Name: stock_transactions stock_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions
    ADD CONSTRAINT stock_transactions_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: time_entries time_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.time_entries
    ADD CONSTRAINT time_entries_pkey PRIMARY KEY (id);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: corporate_accounts uk9q6xutv9qhj0e71pgxgo4iv44; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.corporate_accounts
    ADD CONSTRAINT uk9q6xutv9qhj0e71pgxgo4iv44 UNIQUE (customer_id);


--
-- Name: data_subject_requests uk9s68e2mt0lakdwymsk7qtp2g5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_subject_requests
    ADD CONSTRAINT uk9s68e2mt0lakdwymsk7qtp2g5 UNIQUE (request_number);


--
-- Name: payment_provider_configs uk_payment_provider_configs_business_provider; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_provider_configs
    ADD CONSTRAINT uk_payment_provider_configs_business_provider UNIQUE (business_id, provider);


--
-- Name: platform_payment_provider_configs uk_platform_payment_provider_configs_provider; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_payment_provider_configs
    ADD CONSTRAINT uk_platform_payment_provider_configs_provider UNIQUE (provider);


--
-- Name: notification_templates uk_template_name_per_business; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT uk_template_name_per_business UNIQUE (business_id, name);


--
-- Name: user_roles uk_user_role_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT uk_user_role_scope UNIQUE (user_id, role_id, business_id, shop_id);


--
-- Name: password_reset_tokens ukajre85ybxavf1tt4omkrs5p6g; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT ukajre85ybxavf1tt4omkrs5p6g UNIQUE (token_hash);


--
-- Name: compliance_reports ukck6vcnjgy36m30vn81648nneq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compliance_reports
    ADD CONSTRAINT ukck6vcnjgy36m30vn81648nneq UNIQUE (report_number);


--
-- Name: users ukdu5v5sr43g5bfnji4vb8hg5s3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT ukdu5v5sr43g5bfnji4vb8hg5s3 UNIQUE (phone);


--
-- Name: shop_stock ukh06lmfsxtcq6hdc85n4yedsf9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_stock
    ADD CONSTRAINT ukh06lmfsxtcq6hdc85n4yedsf9 UNIQUE (shop_id, item_id);


--
-- Name: billing_payments ukhba9513xself4lmnly291tk1m; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_payments
    ADD CONSTRAINT ukhba9513xself4lmnly291tk1m UNIQUE (idempotency_key);


--
-- Name: billing_invoices ukim57aybidf7lye81st8lv8qg5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT ukim57aybidf7lye81st8lv8qg5 UNIQUE (invoice_number);


--
-- Name: customer_preferences ukjn6vra9jbtq1t9ix1dc57fh6r; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferences
    ADD CONSTRAINT ukjn6vra9jbtq1t9ix1dc57fh6r UNIQUE (customer_id);


--
-- Name: invoices ukl1x55mfsay7co0r3m9ynvipd5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT ukl1x55mfsay7co0r3m9ynvipd5 UNIQUE (invoice_number);


--
-- Name: auth_credentials uklfugpmelxwee1rvk4cnuqhd2c; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_credentials
    ADD CONSTRAINT uklfugpmelxwee1rvk4cnuqhd2c UNIQUE (user_id);


--
-- Name: billing_coupons ukmuj7v521wiv50083lqmi7fwrl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_coupons
    ADD CONSTRAINT ukmuj7v521wiv50083lqmi7fwrl UNIQUE (code);


--
-- Name: billing_invoices ukn3xea7hborbqm582jnww2oy4r; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT ukn3xea7hborbqm582jnww2oy4r UNIQUE (idempotency_key);


--
-- Name: system_settings uknm18l4pyovtvd8y3b3x0l2y64; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT uknm18l4pyovtvd8y3b3x0l2y64 UNIQUE (setting_key);


--
-- Name: refresh_tokens uko2mlirhldriil2y7krapq4frt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);


--
-- Name: roles ukofx66keruapi6vyqpv6f2or37; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT ukofx66keruapi6vyqpv6f2or37 UNIQUE (name);


--
-- Name: permissions ukpnvtwliis6p05pn6i3ndjrqt2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT ukpnvtwliis6p05pn6i3ndjrqt2 UNIQUE (name);


--
-- Name: users ukr43af9ap4edm43mmtq01oddj6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT ukr43af9ap4edm43mmtq01oddj6 UNIQUE (username);


--
-- Name: order_item_units uks0vkyi3dt0wdrhv4ks3a06jvr; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item_units
    ADD CONSTRAINT uks0vkyi3dt0wdrhv4ks3a06jvr UNIQUE (barcode);


--
-- Name: billing_payments uq_billing_payment_txn; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_payments
    ADD CONSTRAINT uq_billing_payment_txn UNIQUE (gateway, gateway_transaction_id);


--
-- Name: billing_plan_features uq_billing_plan_feature; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_features
    ADD CONSTRAINT uq_billing_plan_feature UNIQUE (plan_id, feature_key);


--
-- Name: billing_plan_versions uq_billing_plan_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_versions
    ADD CONSTRAINT uq_billing_plan_version UNIQUE (plan_id, effective_from);


--
-- Name: billing_subscription_features uq_billing_subscription_feature; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_features
    ADD CONSTRAINT uq_billing_subscription_feature UNIQUE (subscription_id, feature_key);


--
-- Name: order_item_units uq_item_unit; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item_units
    ADD CONSTRAINT uq_item_unit UNIQUE (order_item_id, unit_number);


--
-- Name: user_notification_preferences uq_notif_pref_user_channel_type; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notification_preferences
    ADD CONSTRAINT uq_notif_pref_user_channel_type UNIQUE (user_id, channel, notification_type);


--
-- Name: user_devices user_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_pkey PRIMARY KEY (id);


--
-- Name: user_notification_preferences user_notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_pkey PRIMARY KEY (id);


--
-- Name: user_permissions user_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: user_shops user_shops_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_shops
    ADD CONSTRAINT user_shops_pkey PRIMARY KEY (user_id, shop_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_alert_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_item ON public.stock_alerts USING btree (item_id);


--
-- Name: idx_alert_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_shop ON public.stock_alerts USING btree (shop_id);


--
-- Name: idx_alert_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_status ON public.stock_alerts USING btree (status);


--
-- Name: idx_attendance_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_date ON public.attendance USING btree (date);


--
-- Name: idx_attendance_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_employee ON public.attendance USING btree (employee_id);


--
-- Name: idx_attendance_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_status ON public.attendance USING btree (status);


--
-- Name: idx_audit_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_action ON public.audit_logs USING btree (action);


--
-- Name: idx_audit_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_business ON public.audit_logs USING btree (business_id);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON public.audit_logs USING btree (entity_type, entity_id);


--
-- Name: idx_audit_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_ip ON public.audit_logs USING btree (ip_address);


--
-- Name: idx_audit_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_timestamp ON public.audit_logs USING btree ("timestamp");


--
-- Name: idx_audit_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_user ON public.audit_logs USING btree (user_id);


--
-- Name: idx_billing_coupon_red_coupon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_coupon_red_coupon ON public.billing_coupon_redemptions USING btree (coupon_id, business_id);


--
-- Name: idx_billing_coupons_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_coupons_status ON public.billing_coupons USING btree (status);


--
-- Name: idx_billing_invoices_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_invoices_status ON public.billing_invoices USING btree (status);


--
-- Name: idx_billing_invoices_sub; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_invoices_sub ON public.billing_invoices USING btree (subscription_id);


--
-- Name: idx_billing_line_items_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_line_items_invoice ON public.billing_invoice_line_items USING btree (invoice_id);


--
-- Name: idx_billing_payment_methods_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_payment_methods_business ON public.billing_payment_methods USING btree (business_id);


--
-- Name: idx_billing_payments_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_payments_invoice ON public.billing_payments USING btree (invoice_id);


--
-- Name: idx_billing_plan_features_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_plan_features_plan ON public.billing_plan_features USING btree (plan_id);


--
-- Name: idx_billing_plan_versions_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_plan_versions_plan ON public.billing_plan_versions USING btree (plan_id);


--
-- Name: idx_billing_plans_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_plans_status ON public.billing_plans USING btree (status);


--
-- Name: idx_billing_sub_coupons_coupon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_sub_coupons_coupon ON public.billing_subscription_coupons USING btree (coupon_id);


--
-- Name: idx_billing_sub_events_sub; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_sub_events_sub ON public.billing_subscription_events USING btree (subscription_id);


--
-- Name: idx_billing_sub_features_sub; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_sub_features_sub ON public.billing_subscription_features USING btree (subscription_id);


--
-- Name: idx_billing_subs_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_subs_business ON public.billing_subscriptions USING btree (business_id);


--
-- Name: idx_billing_subs_next_retry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_subs_next_retry ON public.billing_subscriptions USING btree (next_retry_at);


--
-- Name: idx_billing_subs_period_end; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_subs_period_end ON public.billing_subscriptions USING btree (current_period_end);


--
-- Name: idx_billing_subs_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_subs_status ON public.billing_subscriptions USING btree (status);


--
-- Name: idx_billing_usage_sub_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_billing_usage_sub_key ON public.billing_usage_records USING btree (subscription_id, feature_key, recorded_at);


--
-- Name: idx_business_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_business_plan ON public.businesses USING btree (plan);


--
-- Name: idx_business_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_business_status ON public.businesses USING btree (status);


--
-- Name: idx_compliance_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compliance_business ON public.compliance_reports USING btree (business_id);


--
-- Name: idx_compliance_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compliance_period ON public.compliance_reports USING btree (period_start, period_end);


--
-- Name: idx_compliance_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_compliance_type ON public.compliance_reports USING btree (report_type);


--
-- Name: idx_consent_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_consent_customer ON public.consent_records USING btree (customer_id);


--
-- Name: idx_consent_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_consent_status ON public.consent_records USING btree (status);


--
-- Name: idx_consent_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_consent_type ON public.consent_records USING btree (consent_type);


--
-- Name: idx_customer_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_business ON public.customers USING btree (business_id);


--
-- Name: idx_customer_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_email ON public.customers USING btree (email);


--
-- Name: idx_customer_loyalty_tier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_loyalty_tier ON public.customers USING btree (loyalty_tier);


--
-- Name: idx_customer_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_phone ON public.customers USING btree (phone);


--
-- Name: idx_customer_rfm_segment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_rfm_segment ON public.customers USING btree (rfm_segment);


--
-- Name: idx_device_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_active ON public.user_devices USING btree (is_active);


--
-- Name: idx_device_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_token ON public.user_devices USING btree (push_token);


--
-- Name: idx_device_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_user ON public.user_devices USING btree (user_id);


--
-- Name: idx_discrepancies_business_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_discrepancies_business_status ON public.order_discrepancies USING btree (business_id, status);


--
-- Name: idx_discrepancies_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_discrepancies_created ON public.order_discrepancies USING btree (created_at);


--
-- Name: idx_discrepancies_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_discrepancies_order ON public.order_discrepancies USING btree (order_id);


--
-- Name: idx_discrepancies_reported_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_discrepancies_reported_by ON public.order_discrepancies USING btree (reported_by);


--
-- Name: idx_dsr_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dsr_customer ON public.data_subject_requests USING btree (customer_id);


--
-- Name: idx_dsr_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dsr_status ON public.data_subject_requests USING btree (status);


--
-- Name: idx_dsr_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dsr_type ON public.data_subject_requests USING btree (request_type);


--
-- Name: idx_expense_business_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expense_business_category ON public.expenses USING btree (business_id, category);


--
-- Name: idx_expense_business_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expense_business_date ON public.expenses USING btree (business_id, expense_date);


--
-- Name: idx_garment_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_garment_active ON public.garment_types USING btree (business_id, is_active);


--
-- Name: idx_garment_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_garment_business ON public.garment_types USING btree (business_id);


--
-- Name: idx_inventory_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_category ON public.inventory_items USING btree (category);


--
-- Name: idx_inventory_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_supplier ON public.inventory_items USING btree (supplier_id);


--
-- Name: idx_iwi_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iwi_item ON public.item_worker_interactions USING btree (item_id);


--
-- Name: idx_iwi_worker; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iwi_worker ON public.item_worker_interactions USING btree (worker_id);


--
-- Name: idx_log_notification; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_notification ON public.notification_logs USING btree (notification_id);


--
-- Name: idx_log_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_recipient ON public.notification_logs USING btree (recipient);


--
-- Name: idx_log_sent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_sent ON public.notification_logs USING btree (sent_at);


--
-- Name: idx_log_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_status ON public.notification_logs USING btree (status);


--
-- Name: idx_notif_delivery_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_delivery_business ON public.notification_deliveries USING btree (business_id);


--
-- Name: idx_notif_delivery_event_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_delivery_event_delivery ON public.notification_delivery_events USING btree (delivery_id);


--
-- Name: idx_notif_delivery_event_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_delivery_event_time ON public.notification_delivery_events USING btree (occurred_at);


--
-- Name: idx_notif_delivery_notification; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_delivery_notification ON public.notification_deliveries USING btree (notification_id);


--
-- Name: idx_notif_delivery_retry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_delivery_retry ON public.notification_deliveries USING btree (status, next_retry_at);


--
-- Name: idx_notif_outbox_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_outbox_business ON public.notification_outbox USING btree (business_id);


--
-- Name: idx_notif_outbox_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_outbox_claim ON public.notification_outbox USING btree (status, next_attempt_at);


--
-- Name: idx_notif_outbox_schedule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_outbox_schedule ON public.notification_outbox USING btree (scheduled_for);


--
-- Name: idx_notification_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_created ON public.notifications USING btree (created_at);


--
-- Name: idx_notification_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_status ON public.notifications USING btree (status);


--
-- Name: idx_notification_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_type ON public.notifications USING btree (type);


--
-- Name: idx_notification_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_user ON public.notifications USING btree (user_id);


--
-- Name: idx_order_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_customer ON public.orders USING btree (customer_id);


--
-- Name: idx_order_dates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_dates ON public.orders USING btree (received_at);


--
-- Name: idx_order_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_status ON public.orders USING btree (status);


--
-- Name: idx_perf_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_perf_employee ON public.employee_performance USING btree (employee_id);


--
-- Name: idx_perf_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_perf_period ON public.employee_performance USING btree (period_start, period_end);


--
-- Name: idx_po_expected; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_expected ON public.purchase_orders USING btree (expected_delivery);


--
-- Name: idx_po_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_status ON public.purchase_orders USING btree (status);


--
-- Name: idx_po_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_supplier ON public.purchase_orders USING btree (supplier_id);


--
-- Name: idx_pricing_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pricing_business ON public.service_garment_pricing USING btree (business_id);


--
-- Name: idx_pricing_garment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pricing_garment ON public.service_garment_pricing USING btree (garment_type_id);


--
-- Name: idx_pricing_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pricing_service ON public.service_garment_pricing USING btree (service_type_id);


--
-- Name: idx_retention_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retention_business ON public.data_retention_policies USING btree (business_id);


--
-- Name: idx_retention_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retention_entity ON public.data_retention_policies USING btree (entity_type);


--
-- Name: idx_schedule_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedule_date ON public.employee_schedules USING btree (date);


--
-- Name: idx_schedule_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedule_employee ON public.employee_schedules USING btree (employee_id);


--
-- Name: idx_schedule_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedule_shop ON public.employee_schedules USING btree (shop_id);


--
-- Name: idx_security_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_ip ON public.security_events USING btree (ip_address);


--
-- Name: idx_security_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_timestamp ON public.security_events USING btree ("timestamp");


--
-- Name: idx_security_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_type ON public.security_events USING btree (event_type);


--
-- Name: idx_security_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_user ON public.security_events USING btree (user_id);


--
-- Name: idx_shift_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_date ON public.employee_shifts USING btree (date);


--
-- Name: idx_shift_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_employee ON public.employee_shifts USING btree (employee_id);


--
-- Name: idx_shift_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_shop ON public.employee_shifts USING btree (shop_id);


--
-- Name: idx_shift_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_status ON public.employee_shifts USING btree (status);


--
-- Name: idx_shop_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shop_active ON public.shops USING btree (active);


--
-- Name: idx_shop_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shop_business ON public.shops USING btree (business_id);


--
-- Name: idx_shop_stock_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shop_stock_item ON public.shop_stock USING btree (item_id);


--
-- Name: idx_shop_stock_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shop_stock_shop ON public.shop_stock USING btree (shop_id);


--
-- Name: idx_shop_stock_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shop_stock_status ON public.shop_stock USING btree (status);


--
-- Name: idx_stock_requests_business_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_requests_business_status ON public.stock_requests USING btree (business_id, status);


--
-- Name: idx_stock_requests_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_requests_created ON public.stock_requests USING btree (created_at);


--
-- Name: idx_stock_requests_requested_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_requests_requested_by ON public.stock_requests USING btree (requested_by);


--
-- Name: idx_stock_requests_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_requests_shop ON public.stock_requests USING btree (shop_id);


--
-- Name: idx_stock_txn_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_txn_created ON public.stock_transactions USING btree (created_at);


--
-- Name: idx_stock_txn_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_txn_item ON public.stock_transactions USING btree (item_id);


--
-- Name: idx_stock_txn_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_txn_shop ON public.stock_transactions USING btree (shop_id);


--
-- Name: idx_stock_txn_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_txn_type ON public.stock_transactions USING btree (type);


--
-- Name: idx_supplier_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_email ON public.suppliers USING btree (email);


--
-- Name: idx_supplier_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_name ON public.suppliers USING btree (name);


--
-- Name: idx_svc_type_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_svc_type_active ON public.service_types USING btree (business_id, is_active);


--
-- Name: idx_svc_type_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_svc_type_business ON public.service_types USING btree (business_id);


--
-- Name: idx_target_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_date ON public.employee_targets USING btree (date);


--
-- Name: idx_target_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_employee ON public.employee_targets USING btree (employee_id);


--
-- Name: idx_target_metric; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_metric ON public.employee_targets USING btree (metric);


--
-- Name: idx_template_channel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_template_channel ON public.notification_templates USING btree (channel);


--
-- Name: idx_template_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_template_type ON public.notification_templates USING btree (type);


--
-- Name: idx_time_employee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_time_employee ON public.time_entries USING btree (employee_id);


--
-- Name: idx_time_shop; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_time_shop ON public.time_entries USING btree (shop_id);


--
-- Name: idx_time_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_time_timestamp ON public.time_entries USING btree ("timestamp");


--
-- Name: idx_unit_barcode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unit_barcode ON public.order_item_units USING btree (barcode);


--
-- Name: idx_unit_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unit_item ON public.order_item_units USING btree (order_item_id);


--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_email ON public.users USING btree (email);


--
-- Name: idx_user_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_phone ON public.users USING btree (phone);


--
-- Name: idx_user_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_username ON public.users USING btree (username);


--
-- Name: refresh_tokens fk1lih5y2npsf8u5o3vhdb9y0os; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: order_item_units fk39p5wqxkg6hqa5h7jkipct2wo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item_units
    ADD CONSTRAINT fk39p5wqxkg6hqa5h7jkipct2wo FOREIGN KEY (order_item_id) REFERENCES public.order_items(id);


--
-- Name: supplier_categories fk3b2hix6smuvlm3e3bmce6p098; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_categories
    ADD CONSTRAINT fk3b2hix6smuvlm3e3bmce6p098 FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: stock_alerts fk3dc2xxvam360044fwtu5ojxt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_alerts
    ADD CONSTRAINT fk3dc2xxvam360044fwtu5ojxt FOREIGN KEY (item_id) REFERENCES public.inventory_items(id);


--
-- Name: invoice_items fk46ae0lhu1oqs7cv91fn6y9n7w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT fk46ae0lhu1oqs7cv91fn6y9n7w FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);


--
-- Name: auth_credentials fk46e9qm3gfbpgb3h4ckyqwmdam; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_credentials
    ADD CONSTRAINT fk46e9qm3gfbpgb3h4ckyqwmdam FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: purchase_order_items fk5y0w29ahv8gqn5hq6ug5f9u9o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT fk5y0w29ahv8gqn5hq6ug5f9u9o FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id);


--
-- Name: billing_subscription_coupons fk67fvx98lel4tngiiqds2klh52; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_coupons
    ADD CONSTRAINT fk67fvx98lel4tngiiqds2klh52 FOREIGN KEY (subscription_id) REFERENCES public.billing_subscriptions(id);


--
-- Name: refunds fk768b4111uvkogqvtm1c4xfykp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT fk768b4111uvkogqvtm1c4xfykp FOREIGN KEY (payment_id) REFERENCES public.order_payments(id);


--
-- Name: billing_subscriptions fk79t5gym5avuioewqehhjp40am; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscriptions
    ADD CONSTRAINT fk79t5gym5avuioewqehhjp40am FOREIGN KEY (pending_plan_id) REFERENCES public.billing_plans(id);


--
-- Name: defects fk8jhgu09m104b2q3lsgh85w4f6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defects
    ADD CONSTRAINT fk8jhgu09m104b2q3lsgh85w4f6 FOREIGN KEY (quality_check_id) REFERENCES public.quality_checks(id);


--
-- Name: customer_preferences fk94ljqtwhfb80kub1lc214bkbd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferences
    ADD CONSTRAINT fk94ljqtwhfb80kub1lc214bkbd FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: billing_subscriptions fk961mfuebbyegbj49f6e1aqijp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscriptions
    ADD CONSTRAINT fk961mfuebbyegbj49f6e1aqijp FOREIGN KEY (plan_version_id) REFERENCES public.billing_plan_versions(id);


--
-- Name: billing_invoice_line_items fk9op24bnm95655al1c401mksof; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_invoice_line_items
    ADD CONSTRAINT fk9op24bnm95655al1c401mksof FOREIGN KEY (invoice_id) REFERENCES public.billing_invoices(id);


--
-- Name: item_status_history fk9ukfkvurpyor2i723grec2d4g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_status_history
    ADD CONSTRAINT fk9ukfkvurpyor2i723grec2d4g FOREIGN KEY (item_id) REFERENCES public.order_items(id);


--
-- Name: order_items fkbioxgbv59vetrxe0ejfubep1w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: shop_operating_hours fkbqeh2m5rx3xd085u34ym5i79g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_operating_hours
    ADD CONSTRAINT fkbqeh2m5rx3xd085u34ym5i79g FOREIGN KEY (shop_id) REFERENCES public.shops(id);


--
-- Name: stock_transactions fkbu0o4vmyocuoka0c99p4xp8dy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transactions
    ADD CONSTRAINT fkbu0o4vmyocuoka0c99p4xp8dy FOREIGN KEY (item_id) REFERENCES public.inventory_items(id);


--
-- Name: role_permissions fkegdk29eiy7mdtefy5c7eirr6e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: billing_subscription_coupons fkegroi2xql7fnm35gahnojycqc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_coupons
    ADD CONSTRAINT fkegroi2xql7fnm35gahnojycqc FOREIGN KEY (coupon_id) REFERENCES public.billing_coupons(id);


--
-- Name: user_shops fkenr5dss7de9aanjibrdbrmaee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_shops
    ADD CONSTRAINT fkenr5dss7de9aanjibrdbrmaee FOREIGN KEY (shop_id) REFERENCES public.shops(id);


--
-- Name: billing_subscriptions fkexjja367o45yvkkoh519f4iok; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscriptions
    ADD CONSTRAINT fkexjja367o45yvkkoh519f4iok FOREIGN KEY (plan_id) REFERENCES public.billing_plans(id);


--
-- Name: loyalty_transactions fkgjaecj4l1n9mkh4k0r2q15v0k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_transactions
    ADD CONSTRAINT fkgjaecj4l1n9mkh4k0r2q15v0k FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: order_notes fkgl7kbn92v2whrvmco2ygu3cdt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_notes
    ADD CONSTRAINT fkgl7kbn92v2whrvmco2ygu3cdt FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: shop_stock fkhhti0kgyae2c0f9lrlbn3xbps; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_stock
    ADD CONSTRAINT fkhhti0kgyae2c0f9lrlbn3xbps FOREIGN KEY (item_id) REFERENCES public.inventory_items(id);


--
-- Name: billing_plan_versions fkhnxpp56yuflvra7b9xyl9jtv4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_versions
    ADD CONSTRAINT fkhnxpp56yuflvra7b9xyl9jtv4 FOREIGN KEY (plan_id) REFERENCES public.billing_plans(id);


--
-- Name: billing_subscription_features fkhx9gcgigcatjquy0gm1vtedb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_subscription_features
    ADD CONSTRAINT fkhx9gcgigcatjquy0gm1vtedb FOREIGN KEY (subscription_id) REFERENCES public.billing_subscriptions(id);


--
-- Name: password_reset_tokens fkk3ndxg5xp6v7wd4gjyusp15gq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT fkk3ndxg5xp6v7wd4gjyusp15gq FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_shops fkke3lv5mblsb2orqh2m97kp4w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_shops
    ADD CONSTRAINT fkke3lv5mblsb2orqh2m97kp4w FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_permissions fkkowxl8b2bngrxd1gafh13005u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fkkowxl8b2bngrxd1gafh13005u FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: order_timeline fkku427emlhtsh9ktlgk5duuyv9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_timeline
    ADD CONSTRAINT fkku427emlhtsh9ktlgk5duuyv9 FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_discrepancies fkkxgif96bbpp7kh4b4bn5i9p6b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_discrepancies
    ADD CONSTRAINT fkkxgif96bbpp7kh4b4bn5i9p6b FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: billing_plan_features fkky9cptyypvwqart4ptommnihl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.billing_plan_features
    ADD CONSTRAINT fkky9cptyypvwqart4ptommnihl FOREIGN KEY (plan_id) REFERENCES public.billing_plans(id);


--
-- Name: item_images fklpk4sf9lrg3cx4c4um9tq2ybt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_images
    ADD CONSTRAINT fklpk4sf9lrg3cx4c4um9tq2ybt FOREIGN KEY (item_id) REFERENCES public.order_items(id);


--
-- Name: customer_notes fkmlqmw0fgfmurvcmhkeqtdq7qs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_notes
    ADD CONSTRAINT fkmlqmw0fgfmurvcmhkeqtdq7qs FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: role_permissions fkn5fotdgk8d1xvo8nav9uv3muc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: notification_logs fknc1gydajjr1axlduw0ttn7nc9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_logs
    ADD CONSTRAINT fknc1gydajjr1axlduw0ttn7nc9 FOREIGN KEY (notification_id) REFERENCES public.notifications(id);


--
-- Name: checklist_items fko0bxgk5uvb74n1u5dia2ae27x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_items
    ADD CONSTRAINT fko0bxgk5uvb74n1u5dia2ae27x FOREIGN KEY (checklist_id) REFERENCES public.quality_checklists(id);


--
-- Name: purchase_order_items fko7s0t0qc8uibo9mffoa6pwn5g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT fko7s0t0qc8uibo9mffoa6pwn5g FOREIGN KEY (item_id) REFERENCES public.inventory_items(id);


--
-- Name: user_permissions fkq4qlrabt4s0etm9tfkoqfuib1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fkq4qlrabt4s0etm9tfkoqfuib1 FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: check_results fkq6ec5nqlbmgo7q25xm6f6l3p2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_results
    ADD CONSTRAINT fkq6ec5nqlbmgo7q25xm6f6l3p2 FOREIGN KEY (quality_check_id) REFERENCES public.quality_checks(id);


--
-- Name: purchase_orders fkrpdasmb8y8xs5tiy4369xpinq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT fkrpdasmb8y8xs5tiy4369xpinq FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: customer_addresses fkrvr6wl9gll7u98cda18smugp4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT fkrvr6wl9gll7u98cda18smugp4 FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: defect_images fksx2q1e0v0q7qhapvgytn43jli; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_images
    ADD CONSTRAINT fksx2q1e0v0q7qhapvgytn43jli FOREIGN KEY (defect_id) REFERENCES public.defects(id);


--
-- Name: order_tags fkt1hkni4l9vdd1iutmfrvo4cew; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_tags
    ADD CONSTRAINT fkt1hkni4l9vdd1iutmfrvo4cew FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: stock_requests fktfv33wxj0tw72q81jledigjvc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_requests
    ADD CONSTRAINT fktfv33wxj0tw72q81jledigjvc FOREIGN KEY (item_id) REFERENCES public.inventory_items(id);


--
-- PostgreSQL database dump complete
--

\unrestrict 5EgqTbEymbrfRg1hcho2M6xMgXroM1Cqa1qvfUPjfkTlFf9xzR89md4YRX6KMmD

